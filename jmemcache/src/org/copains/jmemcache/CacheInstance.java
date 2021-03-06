package org.copains.jmemcache;

import java.util.Calendar;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.copains.jmemcache.interfaces.JMemCacheable;
import org.copains.jmemcache.objects.GenericMemCacheable;

public class CacheInstance {
	
	/*
	 * cache lifetime in milliseconds
	 */
	private long cacheLifetime;
	
	private int maxElements = -1;
	
	private String instanceName; 
	
	private Hashtable<String, JMemCacheable> cache;
	private Hashtable<String, GenericMemCacheable> genericCache;
	
	private long oldestElementDate = 0;
	private String oldestElementKey = null;
	
	private long gcCalls = 0;
	
	public CacheInstance(String name)
	{
		instanceName = name;
	}
	
	public String addToCache(JMemCacheable cacheable)
	{
		if (null == cacheable)
			return (null);
			
		if (null == cache)
			cache = new Hashtable<String, JMemCacheable>();
		if (!checkCapacity())
		{
			if (gc() < 1)
				removeOldestElement();
		}
		Calendar cal = Calendar.getInstance();
		cacheable.setExpirationDate(cal.getTimeInMillis()+cacheLifetime);
		if (null != cache.put(cacheable.getKey(), cacheable))
			return (cacheable.getKey());
		return (null);
	}
	
	public String addToCache(Object o,String key)
	{
		if (null == o)
			return (null);
		if ((null == key) || (key.length() < 1))
			return (null);
		
		if (null == genericCache)
			genericCache = new Hashtable<String, GenericMemCacheable>();

		if (!checkCapacity())
		{
			if (gc() < 1)
				removeOldestElement();
		}
		GenericMemCacheable gmc = new GenericMemCacheable(o);
		Calendar cal = Calendar.getInstance();
		long exp = cal.getTimeInMillis() + cacheLifetime;
		gmc.setExpirationDate(exp);
		gmc.setKey(key);
		if (null != genericCache.put(key, gmc))
			return (key);
		return (null);
	}
	
	/**
	 * returns object in cache or update it if necessary (if obect type is generic and object is expired
	 * then returns null) 
	 * @param key the unique key used to store the object
	 * @return object in cache or null if the object type is generic and object has expired
	 */
	public Object get(String key)
	{
		if ((null == key) || (key.length() < 1))
			return (null);
		
		if (null != cache)
		{
			JMemCacheable obj = cache.get(key);
			if (null == obj)
				return (null);
			if (hasExpired(obj))
				obj.update();
			return (obj);
		}
		if (null != genericCache)
		{
			GenericMemCacheable obj = genericCache.get(key);
			if (null == obj)
				return (null);
			if (hasExpired(obj))
				return (null);
			return (obj.getCachedObject());			
		}
		return (null);
	}

	/**
	 * removes an object from memory cache
	 * @return true on success
	 */
	public boolean delete(String key)
	{
		if ((null == key) || (key.length() < 1))
			return (false);
		if (null != cache)
			cache.remove(key);
		if (null != genericCache)
			genericCache.remove(key);
		return (true);
	}
	
	/**
	 * remove all elements from this cache instance
	 */
	protected void truncate()
	{
		cache = null;
		genericCache = null;
	}
	
	/**
	 * count objects in this cache instance
	 * @return the count result
	 */
	public int countElements()
	{
		if (null != cache)
			return (cache.size());
		if (null != genericCache)
			return (genericCache.size());
		return (0);
	}
	
	/**
	 * returns a vector of all cached elements !
	 * @return
	 */
	public Vector<Object> getElements()
	{
		if (null != cache)
		{
			Enumeration<JMemCacheable> e = cache.elements();
			Vector<Object> ret = new Vector<Object>();
			while (e.hasMoreElements())
			{
				ret.add(e.nextElement());
			}
			return (ret);
		}
		if (null != genericCache)
		{
			Enumeration<GenericMemCacheable> e = genericCache.elements();
			Vector<Object> ret = new Vector<Object>();
			while (e.hasMoreElements())
			{
				ret.add(e.nextElement().getCachedObject());
			}
			return (ret);
		}
		return (null);
	}
	
	/**
	 * This this the garbage collector method 
	 * @return the number of items removed from cache (expired)
	 */
	public int gc()
	{
		int i = 0;
		gcCalls ++;
		if ((null != cache) &&(cache.size() > 0))
		{
			Enumeration<JMemCacheable> e = cache.elements();
			while (e.hasMoreElements())
			{
				JMemCacheable obj = e.nextElement();
				if (hasExpired(obj))
				{
					cache.remove(obj.getKey());
					i++;
				}
				else
				{
					if ((oldestElementDate == 0) || (oldestElementDate > obj.getExpirationDate()))
					{
						oldestElementDate = obj.getExpirationDate();
						oldestElementKey = obj.getKey();
					}
				}
			}
		}
		if ((null != genericCache) &&(genericCache.size() > 0))
		{
			Enumeration<GenericMemCacheable> e = genericCache.elements();
			while (e.hasMoreElements())
			{
				GenericMemCacheable obj = e.nextElement();
				if (hasExpired(obj))
				{
					genericCache.remove(obj.getKey());
					i++;
				}
				else
				{
					if ((oldestElementDate == 0) || (oldestElementDate > obj.getExpirationDate()))
					{
						oldestElementDate = obj.getExpirationDate();
						oldestElementKey = obj.getKey();
					}
				}
			}
		}
		return (i);
	}
	
	private void removeOldestElement()
	{
		if (null != oldestElementKey)
		{
			delete(oldestElementKey);
			oldestElementDate = 0;
			oldestElementKey = null;
		}
	}
	
	/**
	 *  returns true if there's free room.
	 */
	private boolean checkCapacity()
	{
		if (maxElements <= 0)
			return (true);
		else
		{
			if (countElements() >= maxElements)
				return(false);
		}
		return (true);
	}
	
	public long getCacheLifetime() {
		return cacheLifetime;
	}

	public void setCacheLifetime(long cacheLifetime) {
		this.cacheLifetime = cacheLifetime;
	}

	public String getInstanceName() {
		return instanceName;
	}

	public void setInstanceName(String instanceName) {
		this.instanceName = instanceName;
	}
	
	public boolean hasExpired(JMemCacheable obj)
	{
		Calendar cal = Calendar.getInstance();
		if (obj.getExpirationDate() < cal.getTimeInMillis())
			return (true);
		return (false);
	}

	public int getMaxElements() {
		return maxElements;
	}

	public void setMaxElements(int maxElements) {
		this.maxElements = maxElements;
	}

	/**
	 * @return the gcCalls
	 */
	public long getGcCalls() {
		return gcCalls;
	}

}

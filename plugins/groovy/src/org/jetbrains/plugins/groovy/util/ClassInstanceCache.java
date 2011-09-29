package org.jetbrains.plugins.groovy.util;

import com.intellij.util.containers.ConcurrentHashMap;

/**
 * @author Sergey Evdokimov
 */
public class ClassInstanceCache {

  private static final ConcurrentHashMap<String, Object> CACHE = new ConcurrentHashMap<String, Object>();

  private ClassInstanceCache() {
  }

  @SuppressWarnings("unchecked")
  public static <T> T getInstance(String className) {
    Object res = CACHE.get(className);
    if (res != null) return (T)res;

    try {
      Object instance = Class.forName(className).newInstance();

      Object oldValue = CACHE.putIfAbsent(className, instance);
      if (oldValue != null) {
        instance = oldValue;
      }

      return (T)instance;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
}

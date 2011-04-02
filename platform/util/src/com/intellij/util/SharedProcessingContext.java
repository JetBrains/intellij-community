package com.intellij.util;

import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ConcurrentHashMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author peter
 */
public class SharedProcessingContext {
  private final Map<Object, Object> myMap = new ConcurrentHashMap<Object, Object>();

  public Object get(@NotNull @NonNls final String key) {
    return myMap.get(key);
  }

  public void put(@NotNull @NonNls final String key, @NotNull final Object value) {
    myMap.put(key, value);
  }

  public <T> void put(Key<T> key, T value) {
    myMap.put(key, value);
  }

  public <T> T get(Key<T> key) {
    return (T)myMap.get(key);
  }

  @Nullable
  public <T> T get(@NotNull Key<T> key, Object element) {
    Map map = (Map)myMap.get(key);
    if (map == null) {
      return null;
    }
    else {
      return (T) map.get(element);
    }
  }

  public <T> void put(@NotNull Key<T> key, Object element, T value) {
    Map map = (Map)myMap.get(key);
    if (map == null) {
      map = new THashMap();
      myMap.put(key, map);
    }
    map.put(element, value);
  }
}
/*
 * Copyright (c) 2008 Your Corporation. All Rights Reserved.
 */
package com.intellij.util;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class ProcessingContext {
  private Map<Object, Object> myMap;
  private SharedProcessingContext mySharedContext;

  public ProcessingContext() {
  }

  public ProcessingContext(final SharedProcessingContext sharedContext) {
    mySharedContext = sharedContext;
  }

  @NotNull
  public SharedProcessingContext getSharedContext() {
    if (mySharedContext == null) {
      return mySharedContext = new SharedProcessingContext();
    }
    return mySharedContext;
  }

  @SuppressWarnings({"ConstantConditions"})
  public Object get(@NotNull @NonNls final String key) {
    return myMap == null? null : myMap.get(key);
  }

  public void put(@NotNull @NonNls final String key, @NotNull final Object value) {
    checkMapInitialized();
    myMap.put(key, value);
  }

  public <T> void put(Key<T> key, T value) {
    checkMapInitialized();
    myMap.put(key, value);
  }

  @SuppressWarnings({"ConstantConditions"})
  public <T> T get(Key<T> key) {
    return myMap == null ? null : (T)myMap.get(key);
  }

  private void checkMapInitialized() {
    if (myMap == null) myMap = new HashMap<Object, Object>(1);
  }

}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * A wrapper around map, allowing to put and get values by keys in a type-safe way. Used in various extension implementations
 * to manage temporary state. For example:
 * <ul>
 * <li>When creating {@code ElementPattern}, processing context may be used to cache some intermediate data
 * to be shared between pattern parts.</li>
 * <li>Some extensions (e.g, {@code PsiReferenceProvider}, {@code CompletionContributors}) use per-pattern registration. That allows to use
 * {@code ElementPattern#save} to put matched objects into processing contexts and then retrieve those objects inside extension implementation
 * after the matching is complete.</li>
 * </ul>
 * <p>
 * Simple processing context can contain a shared processing context inside, which should be used when iterating over several patterns
 * or extensions, possibly from different plugins. They may still wish to reuse some cached information that a previous extension has already calculated.
 * <p>
 * In this case, a separate ProcessingContext object is created for each of those extensions, but the same {@link SharedProcessingContext}
 * is passed to their constructors. To reuse shared context, extensions are required to work with {@link #getSharedContext()} result.
 * </p>
 * Not thread-safe.
 *
 * @see #get(Key)
 * @see #put(Key, Object)
 * @see #ProcessingContext(SharedProcessingContext)
 */
public final class ProcessingContext {
  private Object singleKey;
  private Object singleValue;
  private Map<Object, Object> myMap;

  private SharedProcessingContext mySharedContext;

  public ProcessingContext() {
  }

  public ProcessingContext(@NotNull SharedProcessingContext sharedContext) {
    mySharedContext = sharedContext;
  }

  public @NotNull SharedProcessingContext getSharedContext() {
    SharedProcessingContext context = mySharedContext;
    if (context == null) {
      mySharedContext = context = new SharedProcessingContext();
    }
    return context;
  }

  public void put(@NotNull @NonNls Object key, @NotNull Object value) {
    putInternal(key, value);
  }

  public <T> void put(@NotNull Key<T> key, T value) {
    putInternal(key, value);
  }

  @SuppressWarnings("unchecked")
  public <T> T get(@NotNull Key<T> key) {
    return (T)get((Object)key);
  }

  public Object get(@NotNull @NonNls Object key) {
    if (key.equals(singleKey)) {
      return singleValue;
    }

    Map<Object, Object> map = myMap;
    return map == null ? null : map.get(key);
  }

  private void putInternal(@NonNls @NotNull Object key, Object value) {
    if (singleKey == null && myMap == null) {
      singleKey = key;
      singleValue = value;
    }
    else {
      if (myMap == null) {
        myMap = new HashMap<>(1);
        myMap.put(singleKey, singleValue);

        singleKey = null;
        singleValue = null;
      }

      myMap.put(key, value);
    }
  }
}

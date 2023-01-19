// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
 * 
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
public class ProcessingContext {
  private Map<Object, Object> myMap;
  private SharedProcessingContext mySharedContext;

  public ProcessingContext() {
  }

  public ProcessingContext(@NotNull SharedProcessingContext sharedContext) {
    mySharedContext = sharedContext;
  }

  @NotNull
  public SharedProcessingContext getSharedContext() {
    SharedProcessingContext context = mySharedContext;
    if (context == null) {
      mySharedContext = context = new SharedProcessingContext();
    }
    return context;
  }

  public Object get(@NotNull @NonNls final Object key) {
    Map<Object, Object> map = myMap;
    return map == null ? null : map.get(key);
  }

  public void put(@NotNull @NonNls final Object key, @NotNull final Object value) {
    ensureMapInitialized().put(key, value);
  }

  public <T> void put(@NotNull Key<T> key, T value) {
    ensureMapInitialized().put(key, value);
  }

  public <T> T get(@NotNull Key<T> key) {
    return (T)get((Object)key);
  }

  @NotNull
  private Map<Object, Object> ensureMapInitialized() {
    Map<Object, Object> map = myMap;
    if (map == null) myMap = map = new HashMap<>(1);
    return map;
  }

}

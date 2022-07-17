// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.frame;

import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueMarkerProvider;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class XValueMarkers<V extends XValue, M> {
  private final XValueMarkerProvider<V, M> myProvider;
  private final Map<M, ValueMarkup> myMarkers;

  private XValueMarkers(@NotNull XValueMarkerProvider<V, M> provider) {
    myProvider = provider;
    myMarkers = Collections.synchronizedMap(new HashMap<>());
  }

  public static <V extends XValue, M> XValueMarkers<V, M> createValueMarkers(@NotNull XValueMarkerProvider<V, M> provider) {
    return new XValueMarkers<>(provider);
  }

  @Nullable
  public ValueMarkup getMarkup(@NotNull XValue value) {
    Class<V> valueClass = myProvider.getValueClass();
    if (!valueClass.isInstance(value)) return null;

    V v = valueClass.cast(value);
    if (!myProvider.canMark(v)) return null;

    M m = myProvider.getMarker(v);
    if (m == null) return null;

    return myMarkers.get(m);
  }

  public boolean canMarkValue(@NotNull XValue value) {
    Class<V> valueClass = myProvider.getValueClass();
    if (!valueClass.isInstance(value)) return false;

    return myProvider.canMark(valueClass.cast(value));
  }

  @Deprecated
  public void markValue(@NotNull XValue value, @NotNull ValueMarkup markup) {
    try {
      markValueAsync(value, markup).blockingGet(5, TimeUnit.SECONDS);
    }
    catch (TimeoutException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  public Promise<Object> markValueAsync(@NotNull XValue value, @NotNull ValueMarkup markup) {
    synchronized (myMarkers) {
      // remove the existing label if any
      myMarkers.entrySet().stream()
        .filter(entry -> markup.getText().equals(entry.getValue().getText()))
        .findFirst()
        .ifPresent(entry -> myMarkers.remove(entry.getKey()));
    }

    //noinspection unchecked
    Promise<M> promise = myProvider.markValueAsync((V)value);
    return promise.then(m -> {
      myMarkers.put(m, markup);
      return null;
    });
  }

  @Deprecated
  public void unmarkValue(@NotNull XValue value) {
    try {
      unmarkValueAsync(value).blockingGet(5, TimeUnit.SECONDS);
    }
    catch (TimeoutException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  public Promise<Object> unmarkValueAsync(@NotNull XValue value) {
    //noinspection unchecked
    final V v = (V)value;
    M m = myProvider.getMarker(v);
    if (m != null) {
      return myProvider.unmarkValueAsync(v, m)
        .onSuccess(__ -> myMarkers.remove(m));
    } else {
      return Promises.resolvedPromise();
    }
  }

  public Map<M, ValueMarkup> getAllMarkers() {
    return Collections.unmodifiableMap(myMarkers);
  }

  public void clear() {
    myMarkers.clear();
  }
}

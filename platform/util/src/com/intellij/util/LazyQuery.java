/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author peter
 */
public abstract class LazyQuery<T> implements Query<T> {
  private final NotNullLazyValue<Query<T>> myQuery = new NotNullLazyValue<Query<T>>() {
    @NotNull
    protected Query<T> compute() {
      return computeQuery();
    }
  };

  @NotNull protected abstract Query<T> computeQuery();

  @NotNull
  public Collection<T> findAll() {
    return myQuery.getValue().findAll();
  }

  public T findFirst() {
    return myQuery.getValue().findFirst();
  }

  public boolean forEach(@NotNull final Processor<T> consumer) {
    return myQuery.getValue().forEach(consumer);
  }

  public T[] toArray(final T[] a) {
    return myQuery.getValue().toArray(a);
  }

  public Iterator<T> iterator() {
    return myQuery.getValue().iterator();
  }
}

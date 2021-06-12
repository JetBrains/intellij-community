// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.function.IntPredicate;

/**
 * @author Eugene Zhuravlev
 */
public abstract class ValueContainer<Value> {
  public interface IntIterator {
    boolean hasNext();

    int next();

    int size();
  }

  @NotNull
  public abstract ValueIterator<Value> getValueIterator();

  public interface ValueIterator<Value> extends Iterator<Value> {
    @NotNull IntIterator getInputIdsIterator();

    @Nullable IntPredicate getValueAssociationPredicate();
  }

  public abstract int size();

  @FunctionalInterface
  public interface ContainerAction<T> {
    boolean perform(int id, T value);
  }

  public final boolean forEach(@NotNull ContainerAction<? super Value> action) {
    for (ValueIterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext();) {
      Value value = valueIterator.next();
      for (IntIterator intIterator = valueIterator.getInputIdsIterator(); intIterator.hasNext();) {
        if (!action.perform(intIterator.next(), value)) {
          return false;
        }
      }
    }
    return true;
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.function.IntPredicate;

/**
 * Container for set of pairs (value, valueOriginId).
 * <br/>
 * Used in inverted indexes: inverted index has structure [value -> (key, keySourceId)*], so it is implemented
 * as (persistent) Map[Value -> ValueContainer[Key]).
 * <br/>
 * (There is a bit of mess with keys/values labels, since in inverted index keys effectively switch roles
 * with values)
 *
 * @author Eugene Zhuravlev
 */
public abstract class ValueContainer<Value> {
  public interface IntIterator {
    boolean hasNext();

    int next();

    int size();
  }

  public abstract @NotNull ValueIterator<Value> getValueIterator();

  public interface ValueIterator<Value> extends Iterator<Value> {
    @NotNull IntIterator getInputIdsIterator();

    @Nullable IntPredicate getValueAssociationPredicate();
  }

  public abstract int size();

  @FunctionalInterface
  public interface ContainerAction<T> {
    boolean perform(int id, T value);
  }

  @FunctionalInterface
  public interface ThrowableContainerProcessor<V, T extends Throwable> {
    boolean process(int id, V value) throws T;
  }

  //TODO RC: .forEach() is synchronized, but .process() is not -- and they are otherwise identical. Why the difference?

  public synchronized final boolean forEach(@NotNull ContainerAction<? super Value> action) {
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

  public final <T extends Throwable> boolean process(@NotNull ThrowableContainerProcessor<? super Value, T> action) throws T {
    for (ValueIterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext();) {
      Value value = valueIterator.next();
      for (IntIterator intIterator = valueIterator.getInputIdsIterator(); intIterator.hasNext();) {
        if (!action.process(intIterator.next(), value)) {
          return false;
        }
      }
    }
    return true;
  }
}

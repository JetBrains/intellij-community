// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing;

import com.intellij.util.indexing.containers.EmptyValueContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.function.IntPredicate;

/**
 * <p>Container for set of pairs (valueOriginId, value), where there is only 1 value for given valueOriginId.</p>
 *
 * <p> This class provides a <b>read-only view</b>, subclasses may provide mutators.</p>
 *
 * <p> Used in inverted indexes: inverted index has structure {@code [value -> (key, keySourceId)*]}, so it is implemented
 * as {@code Map[value -> ValueContainer(keySourceId, key) ]}.<br/>
 * (This is there constraint 'single value per valueOriginId' comes from)</p>
 * <p>
 * (There is a bit of mess with keys/values labels, since in inverted index keys effectively switch roles
 * with values: that is called 'Value' in ValueContainer definition is 'Key' when ValueContainer is utilized in
 * the inverted index -- which is its primary role)
 * </p>
 *
 * @author Eugene Zhuravlev
 */
public abstract class ValueContainer<Value> {

  /** @return empty unmodifiable container */
  public static <V> ValueContainer<V> emptyContainer() {
    //noinspection unchecked
    return EmptyValueContainer.INSTANCE;
  }

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

  /**
   * @return true if all the container data was processed, false if processing was stopped early because the action
   * returns false
   */
  public final synchronized boolean forEach(@NotNull ContainerAction<? super Value> action) {
    for (ValueIterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext(); ) {
      Value value = valueIterator.next();
      for (IntIterator intIterator = valueIterator.getInputIdsIterator(); intIterator.hasNext(); ) {
        if (!action.perform(intIterator.next(), value)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * @return true if all the container data was processed, false if processing was stopped early because the action
   * returns false
   */
  public final <T extends Throwable> boolean process(@NotNull ThrowableContainerProcessor<? super Value, T> action) throws T {
    for (ValueIterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext(); ) {
      Value value = valueIterator.next();
      for (IntIterator intIterator = valueIterator.getInputIdsIterator(); intIterator.hasNext(); ) {
        if (!action.process(intIterator.next(), value)) {
          return false;
        }
      }
    }
    return true;
  }
}

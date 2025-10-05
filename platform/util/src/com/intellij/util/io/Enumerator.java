// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashingStrategy;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
/**
 * Utility class that allows to associate objects with a unique number
 * Null is always associated with a 0
 *
 * @param <T> type of enumerated object
 */
@ApiStatus.Internal
public class Enumerator<T> {
  private static final LoggerRt LOG = LoggerRt.getInstance(Enumerator.class);
  private final Object2IntMap<T> myNumbers;
  private int myNextNumber = 1;

  public Enumerator(int expectNumber) {
    this(expectNumber, HashingStrategy.canonical());
  }

  public Enumerator(int expectNumber, @NotNull HashingStrategy<? super T> strategy) {
    myNumbers =
      strategy == HashingStrategy.canonical() ? new Object2IntOpenHashMap<>(expectNumber) :
      new Object2IntOpenCustomHashMap<>(expectNumber, new Hash.Strategy<T>() {
        @Override
        public int hashCode(@Nullable T o) {
          return strategy.hashCode(o);
        }

        @Override
        public boolean equals(@Nullable T a, @Nullable T b) {
          return strategy.equals(a, b);
        }
      });
  }

  /**
   * Clear all associations
   */
  public void clear() {
    myNumbers.clear();
    myNextNumber = 1;
  }

  /**
   * Associate all objects with numbers
   *
   * @param objects array of objects to be associated
   * @return the mapping of an initial array into associated numbers
   */
  public int @NotNull [] enumerate(T @NotNull [] objects) {
    return enumerate(objects, 0, 0);
  }

  /**
   * Associate a subset of objects with numbers
   *
   * @param objects    array of objects to be associated
   * @param startShift subset start index
   * @param endCut     a number of excluded objects at the end
   * @return the mapping of an initial array into associated numbers
   */
  public int @NotNull [] enumerate(T @NotNull [] objects, final int startShift, final int endCut) {
    int[] idx = ArrayUtil.newIntArray(objects.length - startShift - endCut);
    for (int i = startShift; i < objects.length - endCut; i++) {
      final T object = objects[i];
      final int number = enumerate(object);
      idx[i - startShift] = number;
    }
    return idx;
  }

  /**
   * Associate a single object with a number
   *
   * @param object to be associated
   * @return a number that was associated with an object
   */
  public int enumerate(T object) {
    final int res = enumerateImpl(object);
    return Math.abs(res);
  }

  /**
   * Associate a single object with a number
   *
   * @param object to be associated
   * @return whether a new association was established
   */
  public boolean add(T object) {
    final int res = enumerateImpl(object);
    return res < 0;
  }

  protected int enumerateImpl(T object) {
    if( object == null ) return 0;

    int number = myNumbers.getInt(object);
    if (number == 0) {
      number = myNextNumber++;
      myNumbers.put(object, number);
      return -number;
    }
    return number;
  }

  /**
   * Check whether an object was associated with a number
   */
  public boolean contains(@NotNull T object) {
    return myNumbers.getInt(object) != 0;
  }

  /**
   * Get the number associated with an object
   * Returns 0 for null and non-enumerated objects
   */
  public int get(T object) {
    if (object == null) return 0;
    final int res = myNumbers.getInt(object);

    if (res == 0)
      LOG.error( "Object "+ object + " must be already added to enumerator!" );

    return res;
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    for (Object2IntMap.Entry<T> entry : myNumbers.object2IntEntrySet()) {
      buffer.append(entry.getIntValue()).append(": ").append(entry.getKey()).append("\n");
    }
    return buffer.toString();
  }
}

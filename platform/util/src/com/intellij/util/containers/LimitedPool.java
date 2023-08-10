// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.MathUtil;
import org.jetbrains.annotations.NotNull;

/**
 * <p>A simple object pool which instantiates objects on-demand and keeps up to the given number of objects for later reuse.</p>
 * <p><b>Note:</b> the class is not thread-safe; use {@link Sync synchronized version} for concurrent access.</p>
 *
 * @author max
 */
public class LimitedPool<T> {
  @FunctionalInterface
  public interface ObjectFactory<T> {
    @NotNull T create();
    default void cleanup(@NotNull T t) { }
  }

  private final int myMaxCapacity;
  private final ObjectFactory<T> myFactory;
  private Object[] myStorage = ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  private int myIndex;

  public LimitedPool(int maxCapacity, @NotNull ObjectFactory<T> factory) {
    myMaxCapacity = maxCapacity;
    myFactory = factory;
  }

  public @NotNull T alloc() {
    if (myIndex == 0) {
      return myFactory.create();
    }

    int i = --myIndex;
    @SuppressWarnings("unchecked") T result = (T)myStorage[i];
    myStorage[i] = null;
    return result;
  }

  public void recycle(@NotNull T t) {
    myFactory.cleanup(t);
    if (myIndex >= myMaxCapacity) {
      return;
    }

    ensureCapacity();
    myStorage[myIndex++] = t;
  }

  private void ensureCapacity() {
    if (myStorage.length <= myIndex) {
      int newCapacity = MathUtil.clamp(myStorage.length * 3 / 2, 10, myMaxCapacity);
      myStorage = ArrayUtil.realloc(myStorage, newCapacity, ArrayUtil.OBJECT_ARRAY_FACTORY);
    }
  }

  public static final class Sync<T> extends LimitedPool<T> {
    public Sync(int maxCapacity, @NotNull ObjectFactory<T> factory) {
      super(maxCapacity, factory);
    }

    @Override
    public synchronized @NotNull T alloc() {
      return super.alloc();
    }

    @Override
    public synchronized void recycle(@NotNull T t) {
      super.recycle(t);
    }
  }
}
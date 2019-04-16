// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * <b>Note:</b> the class is not thread-safe; use {@link Sync synchronized version} for concurrent access.
 *
 * @author max
 */
public class LimitedPool<T> {
  public interface ObjectFactory<T> {
    @NotNull T create();
    default void cleanup(@NotNull T t) { }
  }

  private final int myCapacity;
  private final ObjectFactory<T> myFactory;
  private Object[] myStorage;
  private int myIndex;

  public LimitedPool(int capacity, @NotNull ObjectFactory<T> factory) {
    myCapacity = capacity;
    myFactory = factory;
    myStorage = new Object[10];
  }

  @NotNull
  public T alloc() {
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
    if (myIndex >= myCapacity) {
      return;
    }

    ensureCapacity();
    myStorage[myIndex++] = t;
  }

  private void ensureCapacity() {
    if (myStorage.length <= myIndex) {
      int newCapacity = Math.min(myCapacity, myStorage.length * 3 / 2);
      myStorage = ArrayUtil.realloc(myStorage, newCapacity, ArrayUtil.OBJECT_ARRAY_FACTORY);
    }
  }

  public static final class Sync<T> extends LimitedPool<T> {
    public Sync(int capacity, @NotNull ObjectFactory<T> factory) {
      super(capacity, factory);
    }

    @NotNull
    @Override
    public synchronized T alloc() {
      return super.alloc();
    }

    @Override
    public synchronized void recycle(@NotNull T t) {
      super.recycle(t);
    }
  }
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.ReviseWhenPortedToJDK;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.nio.ByteOrder;

/**
 * Implementation of {@link VarHandleWrapper} based on {@link Unsafe}, when the {@link java.lang.invoke.VarHandle} is not available in the classpath
 */
@ReviseWhenPortedToJDK("11") // get rid of unsafe
@ApiStatus.Internal
class VarHandleWrapperUnsafe extends VarHandleWrapper implements VarHandleWrapper.VarHandleWrapperFactory {
  private final long OFFSET;
  private final int ABASE;
  private final int ASHIFT;

  private VarHandleWrapperUnsafe(long OFFSET) {
    this.OFFSET = OFFSET;
    this.ABASE = -1;
    this.ASHIFT = -1;
  }
  private VarHandleWrapperUnsafe(int ABASE, int ASHIFT) {
    this.ABASE = ABASE;
    this.ASHIFT = ASHIFT;
    OFFSET = -1;
  }

  @Override
  public @NotNull VarHandleWrapper create(@NotNull Class<?> containingClass, @NotNull String name, @NotNull Class<?> type) {
    try {
      Field field = containingClass.getDeclaredField(name);
      long offset = Unsafe.objectFieldOffset(field);
      return new VarHandleWrapperUnsafe(offset);
    }
    catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public @NotNull VarHandleWrapper createForArrayElement(@NotNull Class<?> arrayClass) {
    assert arrayClass.isArray();
    int ABASE = Unsafe.arrayBaseOffset(arrayClass);
    int scale = Unsafe.arrayIndexScale(arrayClass);
    if ((scale & (scale - 1)) != 0) {
      throw new Error("data type scale not a power of two");
    }
    int ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
    return new VarHandleWrapperUnsafe(ABASE, ASHIFT);
  }

  @Override
  public boolean compareAndSet(Object thisObject, Object expected, Object actual) {
    assert ASHIFT == -1;
    return Unsafe.compareAndSwapObject(thisObject, OFFSET, expected, actual);
  }

  @Override
  public boolean compareAndSetByte(Object thisObject, byte expected, byte actual) {
    assert ASHIFT == -1;
    // Unsafe does not have compareAndSwapByte like VarHandle

    long intWordOffset = OFFSET & ~3L;          // 4-byte aligned
    int byteIndex = (int)(OFFSET & 3L);   // 0..3 within the word
    int shift = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN ? (3 - byteIndex) * 8 : byteIndex * 8;
    int mask = 0xFF << shift;
    int expBits = (expected & 0xFF) << shift;
    int updBits = (actual & 0xFF) << shift;

    while(true) {
      int cur = Unsafe.getIntVolatile(thisObject, intWordOffset);
      if ((cur & mask) != expBits) {
        return false; // observed byte != expected
      }
      int next = (cur & ~mask) | updBits;
      if (Unsafe.compareAndSwapInt(thisObject, intWordOffset, cur, next)) {
        return true;
      }
      // lost the race on some byte in the same word; retry
    }
  }

  @Override
  public boolean compareAndSetInt(Object thisObject, int expected, int actual) {
    assert ASHIFT == -1;
    return Unsafe.compareAndSwapInt(thisObject, OFFSET, expected, actual);
  }

  @Override
  public boolean compareAndSetLong(Object thisObject, long expected, long actual) {
    assert ASHIFT == -1;
    return Unsafe.compareAndSwapLong(thisObject, OFFSET, expected, actual);
  }

  @Override
  public int getAndAdd(Object thisObject, int value) {
    return Unsafe.getAndAddInt(thisObject, OFFSET, value);
  }

  @Override
  public Object getVolatileArrayElement(Object thisObject, int index) {
    assert OFFSET == -1;
    return Unsafe.getObjectVolatile(thisObject, ((long)index << ASHIFT) + ABASE);
  }

  @Override
  public void setVolatileArrayElement(Object thisObject, int index, Object value) {
    assert OFFSET == -1;
    Unsafe.putObjectVolatile(thisObject, ((long)index << ASHIFT) + ABASE, value);
  }

  @Override
  public boolean compareAndSetArrayElement(Object thisObject, int index, Object expected, Object value) {
    assert OFFSET == -1;
    return Unsafe.compareAndSwapObject(thisObject, ((long)index << ASHIFT) + ABASE, expected, value);
  }

  static void useUnsafeInConcurrentCollections() {
    FACTORY = new VarHandleWrapperUnsafe(-1);
  }
}

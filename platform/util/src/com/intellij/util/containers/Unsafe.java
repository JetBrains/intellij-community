// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

@ApiStatus.Obsolete
@ApiStatus.Internal
public final class Unsafe {
  private static final MethodHandle putObjectVolatile;
  private static final MethodHandle getObjectVolatile;
  private static final MethodHandle compareAndSwapObject;
  private static final MethodHandle compareAndSwapInt;
  private static final MethodHandle getIntVolatile;
  private static final MethodHandle compareAndSwapLong;
  private static final MethodHandle getAndAddInt;
  private static final MethodHandle objectFieldOffset;
  private static final MethodHandle arrayIndexScale;
  private static final MethodHandle arrayBaseOffset;

  private static final Object unsafe;

  static {
    try {
      Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
      unsafe = ReflectionUtil.getStaticFieldValue(unsafeClass, unsafeClass, "theUnsafe");
      if (unsafe == null) {
        throw new RuntimeException("Could not find 'theUnsafe' field in the Unsafe class");
      }
      putObjectVolatile = find("putObjectVolatile", void.class, Object.class, long.class, Object.class);
      getObjectVolatile = find("getObjectVolatile", Object.class, Object.class, long.class);
      compareAndSwapObject = find("compareAndSwapObject", boolean.class, Object.class, long.class, Object.class, Object.class);
      compareAndSwapInt = find("compareAndSwapInt", boolean.class, Object.class, long.class, int.class, int.class);
      getIntVolatile = find("getIntVolatile", int.class, Object.class, long.class);
      compareAndSwapLong = find("compareAndSwapLong", boolean.class, Object.class, long.class, long.class, long.class);
      getAndAddInt = find("getAndAddInt", int.class, Object.class, long.class, int.class);
      objectFieldOffset = find("objectFieldOffset", long.class, Field.class);
      arrayBaseOffset = find("arrayBaseOffset", int.class, Class.class);
      arrayIndexScale = find("arrayIndexScale", int.class, Class.class);
    }
    catch (Throwable t) {
      throw new Error(t);
    }
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static @NotNull Object getUnsafeImplDeprecated() {
    return unsafe;
  }

  private static @NotNull MethodHandle find(String name, Class<?> returnType, Class<?>... params) throws Exception {
    MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
    return publicLookup
      .findVirtual(unsafe.getClass(), name, MethodType.methodType(returnType, params))
      .bindTo(unsafe);
  }

  static boolean compareAndSwapInt(Object object, long offset, int expected, int value) {
    try {
      return (boolean)compareAndSwapInt.invokeExact(object, offset, expected, value);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  static int getIntVolatile(Object object, long offset) {
    try {
      return (int)getIntVolatile.invokeExact(object, offset);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  static boolean compareAndSwapLong(@NotNull Object object, long offset, long expected, long value) {
    try {
      return (boolean)compareAndSwapLong.invokeExact(object, offset, expected, value);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  static int getAndAddInt(@NotNull Object object, long offset, int value) {
    try {
      return (int)getAndAddInt.invokeExact(object, offset, value);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  static Object getObjectVolatile(Object object, long offset) {
    try {
      return getObjectVolatile.invokeExact(object, offset);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  static boolean compareAndSwapObject(Object o, long offset,
                                      Object expected,
                                      Object x) {
    try {
      return (boolean)compareAndSwapObject.invokeExact(o, offset, expected, x);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  static void putObjectVolatile(Object o, long offset, Object x) {
    try {
      putObjectVolatile.invokeExact(o, offset, x);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  static long objectFieldOffset(Field f) {
    try {
      return (long)objectFieldOffset.invokeExact(f);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  static int arrayIndexScale(Class<?> arrayClass) {
    try {
      return (int)arrayIndexScale.invokeExact(arrayClass);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  public static int arrayBaseOffset(Class<?> arrayClass) {
    try {
      return (int)arrayBaseOffset.invokeExact(arrayClass);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }
}

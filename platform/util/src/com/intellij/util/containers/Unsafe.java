// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

@ApiStatus.Internal
public
class Unsafe {
  private static final MethodHandle putObjectVolatile;
  private static final MethodHandle putOrderedLong;
  private static final MethodHandle getObjectVolatile;
  private static final MethodHandle compareAndSwapObject;
  private static final MethodHandle compareAndSwapInt;
  private static final MethodHandle compareAndSwapLong;
  private static final MethodHandle getAndAddInt;
  private static final MethodHandle objectFieldOffset;
  private static final MethodHandle getLong;
  private static final MethodHandle arrayIndexScale;
  private static final MethodHandle arrayBaseOffset;
  private static final MethodHandle putObject;
  private static final MethodHandle getObject;
  private static final MethodHandle putOrderedObject;

  static {
    try {
      putObjectVolatile = find("putObjectVolatile", void.class, Object.class, long.class, Object.class);
      putOrderedLong = find("putOrderedLong", void.class, Object.class, long.class, long.class);
      getObjectVolatile = find("getObjectVolatile", Object.class, Object.class, long.class);
      compareAndSwapObject = find("compareAndSwapObject", boolean.class, Object.class, long.class, Object.class, Object.class);
      compareAndSwapInt = find("compareAndSwapInt", boolean.class, Object.class, long.class, int.class, int.class);
      compareAndSwapLong = find("compareAndSwapLong", boolean.class, Object.class, long.class, long.class, long.class);
      getAndAddInt = find("getAndAddInt", int.class, Object.class, long.class, int.class);
      objectFieldOffset = find("objectFieldOffset", long.class, Field.class);
      getLong = find("getLong", long.class, Object.class, long.class);
      arrayBaseOffset = find("arrayBaseOffset", int.class, Class.class);
      arrayIndexScale = find("arrayIndexScale", int.class, Class.class);
      putObject = find("putObject", void.class, Object.class, long.class, Object.class);
      getObject = find("getObject", Object.class, Object.class, long.class);
      putOrderedObject = find("putOrderedObject", void.class, Object.class, long.class, Object.class);
    }
    catch (Throwable t) {
      throw new Error(t);
    }
  }

  @NotNull
  private static MethodHandle find(String name, Class returnType, Class... params) throws Exception {
    MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
    Object unsafe = ReflectionUtil.getUnsafe();
    return publicLookup
      .findVirtual(unsafe.getClass(), name, MethodType.methodType(returnType, params))
      .bindTo(unsafe);
  }

  static boolean compareAndSwapInt(@NotNull Object object, long offset, int expected, int value) {
    try {
      return (boolean)compareAndSwapInt.invokeExact(object, offset, expected, value);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  public static boolean compareAndSwapLong(@NotNull Object object, long offset, long expected, long value) {
    try {
      return (boolean)compareAndSwapLong.invokeExact(object, offset, expected, value);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }
  static int getAndAddInt(Object object, long offset, int v) {
    try {
      return (int)getAndAddInt.invokeExact(object, offset, v);
    }
    catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
  public static Object getObjectVolatile(Object object, long offset) {
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
  public static void putOrderedLong(Object o, long offset, long x) {
    try {
      putOrderedLong.invokeExact(o, offset, x);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  public static long objectFieldOffset(Field f) {
    try {
      return (long)objectFieldOffset.invokeExact(f);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }
  public static long getLong(Object o, long offset) {
    try {
      return (long)getLong.invokeExact(o, offset);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  public static int arrayIndexScale(Class<?> arrayClass) {
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
  public static void putObject(Object o, long offset, Object x) {
    try {
      putObject.invokeExact(o, offset, x);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }
  public static Object getObject(Object o, long offset) {
    try {
      return getObject.invokeExact(o, offset);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }
  public static void putOrderedObject(Object o, long offset, Object x) {
    try {
      putOrderedObject.invokeExact(o, offset, x);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }
}

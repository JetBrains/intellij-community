// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.concurrency;

import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Utility class similar to {@link java.util.concurrent.atomic.AtomicReferenceFieldUpdater} except:
 * - removed access check in getAndSet() hot path for performance
 * - new methods "forFieldXXX" added that search by field type instead of field name, which is useful in scrambled classes
 */
public final class AtomicFieldUpdater<ContainingClass, FieldType> {
  private static final Object unsafe;

  private static final MethodHandle compareAndSwapIntHandle;
  private static final MethodHandle compareAndSwapLongHandle;
  private static final MethodHandle compareAndSwapObjectHandle;
  private static final MethodHandle putObjectVolatileHandle;
  private static final MethodHandle getObjectVolatileHandle;

  static {
    Class<?> unsafeClass;
    try {
      unsafeClass = Class.forName("sun.misc.Unsafe");
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    unsafe = ReflectionUtil.getStaticFieldValue(unsafeClass, unsafeClass, "theUnsafe");
    if (unsafe == null) {
      throw new RuntimeException("Could not find 'theUnsafe' field in the Unsafe class");
    }
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    try {
      compareAndSwapIntHandle = lookup.findVirtual(unsafeClass, "compareAndSwapInt", MethodType.methodType(boolean.class, Object.class, long.class, int.class, int.class)).bindTo(unsafe);
      compareAndSwapLongHandle = lookup.findVirtual(unsafeClass, "compareAndSwapLong", MethodType.methodType(boolean.class, Object.class, long.class, long.class, long.class)).bindTo(unsafe);
      compareAndSwapObjectHandle = lookup.findVirtual(unsafeClass, "compareAndSwapObject", MethodType.methodType(boolean.class, Object.class, long.class, Object.class, Object.class)).bindTo(unsafe);
      putObjectVolatileHandle = lookup.findVirtual(unsafeClass, "putObjectVolatile", MethodType.methodType(void.class, Object.class, long.class, Object.class)).bindTo(unsafe);
      getObjectVolatileHandle = lookup.findVirtual(unsafeClass, "getObjectVolatile", MethodType.methodType(Object.class, Object.class, long.class)).bindTo(unsafe);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static @NotNull Object getUnsafe() {
    return unsafe;
  }

  private final long offset;

  public static @NotNull <T, V> AtomicFieldUpdater<T, V> forFieldOfType(@NotNull Class<T> ownerClass, @NotNull Class<V> fieldType) {
    return new AtomicFieldUpdater<>(ownerClass, fieldType);
  }

  public static @NotNull <T> AtomicFieldUpdater<T, Long> forLongFieldIn(@NotNull Class<T> ownerClass) {
    return new AtomicFieldUpdater<>(ownerClass, long.class);
  }

  public static @NotNull <T> AtomicFieldUpdater<T, Integer> forIntFieldIn(@NotNull Class<T> ownerClass) {
    return new AtomicFieldUpdater<>(ownerClass, int.class);
  }

  public static @NotNull <O,E> AtomicFieldUpdater<O, E> forField(@NotNull Field field) {
    return new AtomicFieldUpdater<>(field);
  }

  private AtomicFieldUpdater(@NotNull Class<ContainingClass> ownerClass, @NotNull Class<FieldType> fieldType) {
    this(ReflectionUtil.getTheOnlyVolatileInstanceFieldOfClass(ownerClass, fieldType));
  }

  private AtomicFieldUpdater(@NotNull Field field) {
    field.setAccessible(true);
    if (!Modifier.isVolatile(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) throw new IllegalArgumentException(field + " must be volatile instance");
    try {
      MethodHandle objectFieldOffset =
        MethodHandles.publicLookup().findVirtual(unsafe.getClass(), "objectFieldOffset", MethodType.methodType(long.class, Field.class));
      offset = (long)objectFieldOffset.invoke(unsafe, field);
    }
    catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public boolean compareAndSet(@NotNull ContainingClass owner, FieldType expected, FieldType newValue) {
    try {
      return (boolean)compareAndSwapObjectHandle.invokeExact(owner, offset, expected, newValue);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  public boolean compareAndSetLong(@NotNull ContainingClass owner, long expected, long newValue) {
    try {
      return (boolean)compareAndSwapLongHandle.invokeExact(owner, offset, expected, newValue);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  public boolean compareAndSetInt(@NotNull ContainingClass owner, int expected, int newValue) {
    try {
      return (boolean)compareAndSwapIntHandle.invokeExact(owner, offset, expected, newValue);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  public void set(@NotNull ContainingClass owner, FieldType newValue) {
    try {
      putObjectVolatileHandle.invokeExact(owner, offset, newValue);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  public FieldType get(@NotNull ContainingClass owner) {
    try {
      return (FieldType)getObjectVolatileHandle.invokeExact(owner, offset);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }
}

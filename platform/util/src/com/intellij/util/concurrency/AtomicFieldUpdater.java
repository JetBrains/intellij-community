// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.concurrency;

import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Utility class similar to {@link java.util.concurrent.atomic.AtomicReferenceFieldUpdater} except:
 * - removed access check in getAndSet() hot path for performance
 * - new methods "forFieldXXX" added that search by field type instead of field name, which is useful in scrambled classes
 */
public final class AtomicFieldUpdater<ContainingClass, FieldType> {
  private static final Unsafe unsafe;
  static {
    unsafe = ReflectionUtil.getStaticFieldValue(Unsafe.class, Unsafe.class, "theUnsafe");
    if (unsafe == null) {
      throw new RuntimeException("Could not find 'theUnsafe' field in the " + Unsafe.class);
    }
  }

  public static @NotNull Unsafe getUnsafe() {
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
    offset = unsafe.objectFieldOffset(field);
  }

  public boolean compareAndSet(@NotNull ContainingClass owner, FieldType expected, FieldType newValue) {
    return unsafe.compareAndSwapObject(owner, offset, expected, newValue);
  }

  public boolean compareAndSetLong(@NotNull ContainingClass owner, long expected, long newValue) {
    return unsafe.compareAndSwapLong(owner, offset, expected, newValue);
  }

  public boolean compareAndSetInt(@NotNull ContainingClass owner, int expected, int newValue) {
    return unsafe.compareAndSwapInt(owner, offset, expected, newValue);
  }

  public void set(@NotNull ContainingClass owner, FieldType newValue) {
    unsafe.putObjectVolatile(owner, offset, newValue);
  }

  public FieldType get(@NotNull ContainingClass owner) {
    //noinspection unchecked
    return (FieldType)unsafe.getObjectVolatile(owner, offset);
  }
}

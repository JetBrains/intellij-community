// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class Conditions {
  private Conditions() { }

  public static @NotNull <T> Condition<T> alwaysTrue() {
    //noinspection unchecked,deprecation
    return (Condition<T>)Condition.TRUE;
  }

  public static @NotNull <T> Condition<T> alwaysFalse() {
    //noinspection unchecked,deprecation
    return (Condition<T>)Condition.FALSE;
  }

  public static @NotNull <T> Condition<T> notNull() {
    //noinspection unchecked,deprecation
    return (Condition<T>)Condition.NOT_NULL;
  }

  public static @NotNull <T> Condition<T> constant(boolean value) {
    return value ? alwaysTrue() : alwaysFalse();
  }

  public static @NotNull <T> Condition<T> instanceOf(final @NotNull Class<?> clazz) {
    return t -> clazz.isInstance(t);
  }

  public static @NotNull <T> Condition<T> notInstanceOf(final @NotNull Class<?> clazz) {
    return t -> !clazz.isInstance(t);
  }

  public static @NotNull Condition<Class<?>> assignableTo(final @NotNull Class<?> clazz) {
    return t -> clazz.isAssignableFrom(t);
  }

  public static @NotNull <T> Condition<T> instanceOf(final Class<?>... clazz) {
    return t -> {
      for (Class<?> aClass : clazz) {
        if (aClass.isInstance(t)) return true;
      }
      return false;
    };
  }

  public static @NotNull <T> Condition<T> is(final T option) {
    return equalTo(option);
  }

  public static @NotNull <T> Condition<T> equalTo(final Object option) {
    return t -> Comparing.equal(t, option);
  }

  public static @NotNull <T> Condition<T> notEqualTo(final Object option) {
    return t -> !Comparing.equal(t, option);
  }

  @SafeVarargs
  public static @NotNull <T> Condition<T> oneOf(T @NotNull ... options) {
    return oneOf(Arrays.asList(options));
  }

  public static @NotNull <T> Condition<T> oneOf(final @NotNull Collection<? extends T> options) {
    return t -> options.contains(t);
  }

  public static @NotNull <T> Condition<T> not(@NotNull Condition<? super T> c) {
    if (c == alwaysTrue()) return alwaysFalse();
    if (c == alwaysFalse()) return alwaysTrue();
    if (c instanceof Not) {
      //noinspection unchecked
      return (Condition<T>)((Not<T>)c).c;
    }
    return new Not<>(c);
  }

  public static @NotNull <T> Condition<T> and(@NotNull Condition<? super T> c1, @NotNull Condition<? super T> c2) {
    if (c1 == alwaysTrue() || c2 == alwaysFalse()) {
      //noinspection unchecked
      return (Condition<T>)c2;
    }
    if (c2 == alwaysTrue() || c1 == alwaysFalse()) {
      //noinspection unchecked
      return (Condition<T>)c1;
    }
    return new And<>(c1, c2);
  }

  public static @NotNull <T> Condition<T> or(@NotNull Condition<? super T> c1, @NotNull Condition<? super T> c2) {
    if (c1 == alwaysFalse()|| c2 == alwaysTrue()) {
      //noinspection unchecked
      return (Condition<T>)c2;
    }
    if (c2 == alwaysFalse() || c1 == alwaysTrue()) {
      //noinspection unchecked
      return (Condition<T>)c1;
    }
    return new Or<>(c1, c2);
  }

  public static @NotNull <A, B> Condition<A> compose(final @NotNull Function<? super A, B> fun, final @NotNull Condition<? super B> condition) {
    return o -> condition.value(fun.fun(o));
  }

  public static @NotNull <T> Condition<T> cached(@NotNull Condition<? super T> c) {
    return new SoftRefCache<>(c);
  }

  private static final class Not<T> implements Condition<T> {
    final Condition<? super T> c;

    Not(@NotNull Condition<? super T> c) {
      this.c = c;
    }

    @Override
    public boolean value(T value) {
      return !c.value(value);
    }
  }

  private static final class And<T> implements Condition<T> {
    final Condition<? super T> c1;
    final Condition<? super T> c2;

    And(@NotNull Condition<? super T> c1, @NotNull Condition<? super T> c2) {
      this.c1 = c1;
      this.c2 = c2;
    }

    @Override
    public boolean value(T object) {
      return c1.value(object) && c2.value(object);
    }
  }

  private static final class Or<T> implements Condition<T> {
    final Condition<? super T> c1;
    final Condition<? super T> c2;

    Or(@NotNull Condition<? super T> c1, @NotNull Condition<? super T> c2) {
      this.c1 = c1;
      this.c2 = c2;
    }

    @Override
    public boolean value(T object) {
      return c1.value(object) || c2.value(object);
    }
  }

  private static final class SoftRefCache<T> implements Condition<T> {
    private final Map<Integer, Pair<SoftReference<T>, Boolean>> myCache = new HashMap<>();
    private final Condition<? super T> myCondition;

    SoftRefCache(@NotNull Condition<? super T> condition) {
      myCondition = condition;
    }

    @Override
    public boolean value(T object) {
      final int key = object.hashCode();
      final Pair<SoftReference<T>, Boolean> entry = myCache.get(key);
      if (entry == null || entry.first.get() != object) {
        boolean value = myCondition.value(object);
        myCache.put(key, Pair.create(new SoftReference<>(object), value));
        return value;
      }
      return entry.second;
    }
  }
}
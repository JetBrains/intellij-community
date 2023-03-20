// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.reference.SoftReference;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class Conditions {
  private Conditions() { }

  @NotNull
  public static <T> Condition<T> alwaysTrue() {
    //noinspection unchecked,deprecation
    return (Condition<T>)Condition.TRUE;
  }

  @NotNull
  public static <T> Condition<T> alwaysFalse() {
    //noinspection unchecked,deprecation
    return (Condition<T>)Condition.FALSE;
  }

  @NotNull
  public static <T> Condition<T> notNull() {
    //noinspection unchecked,deprecation
    return (Condition<T>)Condition.NOT_NULL;
  }

  @NotNull
  public static <T> Condition<T> constant(boolean value) {
    return value ? alwaysTrue() : alwaysFalse();
  }

  @NotNull
  public static <T> Condition<T> instanceOf(@NotNull final Class<?> clazz) {
    return t -> clazz.isInstance(t);
  }

  @NotNull
  public static <T> Condition<T> notInstanceOf(@NotNull final Class<?> clazz) {
    return t -> !clazz.isInstance(t);
  }

  @NotNull
  public static Condition<Class<?>> assignableTo(@NotNull final Class<?> clazz) {
    return t -> clazz.isAssignableFrom(t);
  }

  @NotNull
  public static <T> Condition<T> instanceOf(final Class<?>... clazz) {
    return t -> {
      for (Class<?> aClass : clazz) {
        if (aClass.isInstance(t)) return true;
      }
      return false;
    };
  }

  @NotNull
  public static <T> Condition<T> is(final T option) {
    return equalTo(option);
  }

  @NotNull
  public static <T> Condition<T> equalTo(final Object option) {
    return t -> Comparing.equal(t, option);
  }

  @NotNull
  public static <T> Condition<T> notEqualTo(final Object option) {
    return t -> !Comparing.equal(t, option);
  }

  @SafeVarargs
  @NotNull
  public static <T> Condition<T> oneOf(T @NotNull ... options) {
    return oneOf(Arrays.asList(options));
  }

  @NotNull
  public static <T> Condition<T> oneOf(@NotNull final Collection<? extends T> options) {
    return t -> options.contains(t);
  }

  @NotNull
  public static <T> Condition<T> not(@NotNull Condition<? super T> c) {
    if (c == alwaysTrue()) return alwaysFalse();
    if (c == alwaysFalse()) return alwaysTrue();
    if (c instanceof Not) {
      //noinspection unchecked
      return (Condition<T>)((Not<T>)c).c;
    }
    return new Not<>(c);
  }

  @NotNull
  public static <T> Condition<T> and(@NotNull Condition<? super T> c1, @NotNull Condition<? super T> c2) {
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

  @NotNull
  public static <T> Condition<T> or(@NotNull Condition<? super T> c1, @NotNull Condition<? super T> c2) {
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

  @NotNull
  public static <A, B> Condition<A> compose(@NotNull final Function<? super A, B> fun, @NotNull final Condition<? super B> condition) {
    return o -> condition.value(fun.fun(o));
  }

  @NotNull
  public static <T> Condition<T> cached(@NotNull Condition<? super T> c) {
    return new SoftRefCache<>(c);
  }

  private static class Not<T> implements Condition<T> {
    final Condition<? super T> c;

    Not(@NotNull Condition<? super T> c) {
      this.c = c;
    }

    @Override
    public boolean value(T value) {
      return !c.value(value);
    }
  }

  private static class And<T> implements Condition<T> {
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

  private static class Or<T> implements Condition<T> {
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
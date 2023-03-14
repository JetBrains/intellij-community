// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Deprecated. Use {@link java.util.function.Function} instead.
 */
@ApiStatus.Obsolete
public final class Functions {
  private static final Function.Mono<?> ID = new Function.Mono<Object>() {
    @Override
    public Object fun(Object o) {
      return o;
    }

    @Override
    public String toString() {
      return "Functions.ID";
    }
  };

  private static final Function<?, String> TO_STRING = new Function<Object, String>() {
    @Override
    public String fun(Object o) {
      return String.valueOf(o);
    }

    @Override
    public String toString() {
      return "Functions.TO_STRING";
    }
  };

  public static @NotNull <A> Function.Mono<A> id() {
    return (Function.Mono<A>)identity();
  }

  public static @NotNull <A, B> Function<A, B> constant(final B b) {
    return a -> b;
  }

  public static @NotNull <A, B> Function<A, B> identity() {
    //noinspection unchecked
    return (Function<A, B>)ID;
  }

  public static @NotNull <A, B, C> Function<A, C> compose(final @NotNull Function<? super A, ? extends B> f1, final @NotNull Function<? super B, ? extends C> f2) {
    if (f1 == identity() || f2 == identity()) {
      if (f1 == f2) {
        return identity();
      }
      if (f1 == identity()) {
        //noinspection unchecked
        return (Function<A, C>)f2;
      }
      //noinspection unchecked
      return (Function<A, C>)f1;
    }
    return a -> f2.fun(f1.fun(a));
  }

  public static @NotNull <A> Function<A, String> TO_STRING() {
    //noinspection unchecked
    return (Function<A, String>)TO_STRING;
  }

  public static @NotNull <A, B> Function<A, B> fromMap(final @NotNull Map<? super A, ? extends B> map) {
    return map::get;
  }

  private static final Function<Pair<?, ?>, Object> PAIR_FIRST = pair -> Pair.getFirst(pair);

  private static final Function<Pair<?, ?>, Object> PAIR_SECOND = pair -> Pair.getSecond(pair);

  public static @NotNull <A> Function<Pair<A, ?>, A> pairFirst() {
    //noinspection unchecked
    return (Function<Pair<A,?>, A>)(Function<?,?>)PAIR_FIRST;
  }

  public static @NotNull <B> Function<Pair<?, B>, B> pairSecond() {
    //noinspection unchecked
    return (Function<Pair<?, B>, B>)(Function<?,?>)PAIR_SECOND;
  }

  private static final Function<Object[], Iterable<Object>> WRAP_ARRAY = t -> t == null ? Collections.emptyList() : Arrays.asList(t);

  public static @NotNull <T> Function<T[], Iterable<T>> wrapArray() {
    //noinspection unchecked
    return (Function<T[], Iterable<T>>)(Function<?,?>)WRAP_ARRAY;
  }
}
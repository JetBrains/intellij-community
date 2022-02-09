// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * @author gregsh
 */
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


  @NotNull
  public static <A> Function.Mono<A> id() {
    return (Function.Mono<A>)identity();
  }

  @NotNull
  public static <A, B> Function<A, B> constant(final B b) {
    return new Function<A, B>() {
      @Override
      public B fun(A a) {
        return b;
      }
    };
  }

  @NotNull
  public static <A, B> Function<A, B> identity() {
    //noinspection unchecked
    return (Function<A, B>)ID;
  }

  @NotNull
  public static <A, B> Function<A, B> cast(@NotNull Class<B> clazz) {
    return identity();
  }

  @NotNull
  public static <A, B, C> Function<A, C> compose(@NotNull final Function<? super A, ? extends B> f1, @NotNull final Function<? super B, ? extends C> f2) {
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
    return new Function<A, C>() {
      @Override
      public C fun(A a) {
        return f2.fun(f1.fun(a));
      }
    };
  }

  @NotNull
  public static <A> Function<A, String> TO_STRING() {
    //noinspection unchecked,deprecation
    return (Function<A, String>)TO_STRING;
  }

  @NotNull
  public static <A, B> Function<A, B> fromMap(@NotNull final Map<? super A, ? extends B> map) {
    return new Function<A, B>() {
      @Override
      public B fun(A a) {
        return map.get(a);
      }
    };
  }

  private static final Function<Pair<?, ?>, Object> PAIR_FIRST = new Function<Pair<?, ?>, Object>() {
    @Override
    public Object fun(Pair<?, ?> pair) {
      return Pair.getFirst(pair);
    }
  };

  private static final Function<Pair<?, ?>, Object> PAIR_SECOND = new Function<Pair<?, ?>, Object>() {
    @Override
    public Object fun(Pair<?, ?> pair) {
      return Pair.getSecond(pair);
    }
  };

  @NotNull
  public static <A> Function<Pair<A, ?>, A> pairFirst() {
    //noinspection unchecked
    return (Function<Pair<A,?>, A>)(Function<?,?>)PAIR_FIRST;
  }

  @NotNull
  public static <B> Function<Pair<?, B>, B> pairSecond() {
    //noinspection unchecked
    return (Function<Pair<?, B>, B>)(Function<?,?>)PAIR_SECOND;
  }

  private static final Function<Object[], Iterable<Object>> WRAP_ARRAY = new Function<Object[], Iterable<Object>>() {
    @Override
    public Iterable<Object> fun(Object[] t) {
      return t == null ? Collections.emptyList() : Arrays.asList(t);
    }
  };

  @NotNull
  public static <T> Function<T[], Iterable<T>> wrapArray() {
    //noinspection unchecked
    return (Function<T[], Iterable<T>>)(Function<?,?>)WRAP_ARRAY;
  }
}
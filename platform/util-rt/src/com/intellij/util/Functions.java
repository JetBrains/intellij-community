/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * @author gregsh
 */
public class Functions {
  @NotNull
  public static <A> Function.Mono<A> id() {
    return (Function.Mono<A>)identity();
  }

  @NotNull
  public static <A, B> Function<A, B> constant(final B b) {
    return new Function<A, B>() {
      public B fun(A a) {
        return b;
      }
    };
  }

  @NotNull
  public static <A, B> Function<A, B> identity() {
    //noinspection deprecation,unchecked
    return (Function<A, B>)Function.ID;
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
      public C fun(A a) {
        return f2.fun(f1.fun(a));
      }
    };
  }

  @NotNull
  public static <A> Function<A, String> TO_STRING() {
    //noinspection unchecked,deprecation
    return (Function<A, String>)Function.TO_STRING;
  }

  @NotNull
  public static <A, B> Function<A, B> fromMap(@NotNull final Map<? super A, ? extends B> map) {
    return new Function<A, B>() {
      public B fun(A a) {
        return map.get(a);
      }
    };
  }

  private static final Function<Pair<?, ?>, Object> PAIR_FIRST = new Function<Pair<?, ?>, Object>() {
    public Object fun(Pair<?, ?> pair) {
      return Pair.getFirst(pair);
    }
  };

  private static final Function<Pair<?, ?>, Object> PAIR_SECOND = new Function<Pair<?, ?>, Object>() {
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
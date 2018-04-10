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

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * @author gregsh
 */
@SuppressWarnings("unchecked")
public class Functions {
  public static <A> Function.Mono<A> id() {
    return (Function.Mono<A>)Function.ID;
  }

  public static <A, B> Function<A, B> constant(final B b) {
    return new Function<A, B>() {
      public B fun(A a) {
        return b;
      }
    };
  }

  public static <A, B> Function<A, B> identity() {
    return Function.ID;
  }

  public static <A, B> Function<A, B> cast(Class<B> clazz) {
    return Function.ID;
  }

  public static <A, B, C> Function<A, C> compose(final Function<A, B> f1, final Function<B, ? extends C> f2) {
    if (f1 == Function.ID || f2 == Function.ID) {
      //noinspection RedundantConditionalExpression
      return f1 == f2 ? Function.ID : f1 == Function.ID ? f2 : f1;
    } 
    return new Function<A, C>() {
      public C fun(A a) {
        return f2.fun(f1.fun(a));
      }
    };
  }

  public static <A> Function<A, String> TO_STRING() {
    return Function.TO_STRING;
  }

  public static <A, B> Function<A, B> fromMap(final Map<A, B> map) {
    return new Function<A, B>() {
      public B fun(A a) {
        return map.get(a);
      }
    };
  }

  private static final Function<Object, Class> TO_CLASS = new Function<Object, Class>() {
    public Class fun(Object o) {
      return o.getClass();
    }
  };

  public static <T> Function<T, Class> TO_CLASS() {
    return (Function<T, Class>)TO_CLASS;
  }

  private static final Function PAIR_FIRST = new Function<Pair<?, ?>, Object>() {
    public Object fun(Pair<?, ?> pair) {
      return Pair.getFirst(pair);
    }
  };

  private static final Function PAIR_SECOND = new Function<Pair<?, ?>, Object>() {
    public Object fun(Pair<?, ?> pair) {
      return Pair.getSecond(pair);
    }
  };

  public static <A> Function<Pair<A, ?>, A> pairFirst() {
    return (Function<Pair<A, ?>, A>)PAIR_FIRST;
  }

  public static <B> Function<Pair<?, B>, B> pairSecond() {
    return (Function<Pair<?, B>, B>)PAIR_SECOND;
  }

  private static final Function WRAP_ARRAY = new Function<Object[], Iterable<Object>>() {
    public Iterable<Object> fun(Object[] t) {
      return t == null ? Collections.emptyList() : Arrays.asList(t);
    }
  };

  public static <T> Function<T[], Iterable<T>> wrapArray() {
    return (Function<T[], Iterable<T>>)WRAP_ARRAY;
  }
}
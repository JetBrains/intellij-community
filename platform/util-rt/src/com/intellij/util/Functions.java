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

import java.util.Map;

/**
 * @author gregsh
 */
public class Functions {

  public static <A, B> Function<A, B> identity() {
    return Function.ID;
  }

  public static <A> Function<A, String> toString() {
    return Function.TO_STRING;
  }

  public static <A, B> Function<A, B> fromMap(final Map<A, B> map) {
    return new Function<A, B>() {
      @Override
      public B fun(A a) {
        return map.get(a);
      }
    };
  }

  private static final Function PAIR_FIRST = new Function<Pair<?, ?>, Object>() {
    @Override
    public Object fun(Pair<?, ?> pair) {
      return Pair.getFirst(pair);
    }
  };

  private static final Function PAIR_SECOND = new Function<Pair<?, ?>, Object>() {
    @Override
    public Object fun(Pair<?, ?> pair) {
      return Pair.getSecond(pair);
    }
  };

  public static <A> Function<Pair<A, ?>, A> pairFirst() {
    //noinspection unchecked
    return (Function<Pair<A, ?>, A>)PAIR_FIRST;
  }

  public static <B> Function<Pair<?, B>, B> pairSecond() {
    //noinspection unchecked
    return (Function<Pair<?, B>, B>)PAIR_SECOND;
  }
}

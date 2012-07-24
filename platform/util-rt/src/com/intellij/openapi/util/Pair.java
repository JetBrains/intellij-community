/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Pair<A, B> {
  public final A first;
  public final B second;

  @NotNull
  public static <A, B> Pair<A, B> create(A first, B second) {
    return new Pair<A, B>(first, second);
  }

  @NotNull
  public static <A, B> Function<A, Pair<A, B>> createFunction(@Nullable final B value) {
    return new Function<A, Pair<A, B>>() {
      public Pair<A, B> fun(A a) {
        return create(a, value);
      }
    };
  }

  @Nullable
  public static <T> T getFirst(@Nullable Pair<T, ?> pair) {
    return pair != null ? pair.first : null;
  }

  @Nullable
  public static <T> T getSecond(@Nullable Pair<?, T> pair) {
    return pair != null ? pair.second : null;
  }

  @SuppressWarnings({"unchecked", "NullableProblems"})
  private static final Pair EMPTY = create(null, null);

  @SuppressWarnings("unchecked")
  public static <A, B> Pair<A, B> empty() {
    return EMPTY;
  }

  public Pair(A first, B second) {
    this.first = first;
    this.second = second;
  }

  public final A getFirst() {
    return first;
  }

  public final B getSecond() {
    return second;
  }

  public final boolean equals(Object o) {
    return o instanceof Pair && Comparing.equal(first, ((Pair)o).first) && Comparing.equal(second, ((Pair)o).second);
  }

  public int hashCode() {
    int result = first != null ? first.hashCode() : 0;
    result = 31 * result + (second != null ? second.hashCode() : 0);
    return result;
  }

  public String toString() {
    return "<" + first + "," + second + ">";
  }
}

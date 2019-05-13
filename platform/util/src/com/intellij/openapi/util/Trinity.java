/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class Trinity<A, B, C> {
  public final A first;
  public final B second;
  public final C third;

  public Trinity(A first, B second, C third) {
    this.third = third;
    this.first = first;
    this.second = second;
  }

  public final A getFirst() {
    return first;
  }

  public final B getSecond() {
    return second;
  }

  public final C getThird() {
    return third;
  }

  public static <A, B, C> Trinity<A, B, C> create(A first, B second, C third) {
    return new Trinity<A,B,C>(first, second,third);
  }

  public static <T> T getFirst(@Nullable Trinity<T, ?, ?> pair) {
    return pair != null ? pair.first : null;
  }
  public static <T> T getSecond(@Nullable Trinity<?, T, ?> pair) {
    return pair != null ? pair.second : null;
  }
  public static <T> T getThird(@Nullable Trinity<?, ?, T> pair) {
    return pair != null ? pair.third : null;
  }

  @Override
  public final boolean equals(Object o){
    return o instanceof Trinity
           && Comparing.equal(first, ((Trinity)o).first)
           && Comparing.equal(second, ((Trinity)o).second)
           && Comparing.equal(third, ((Trinity)o).third);
  }

  @Override
  public final int hashCode(){
    int hashCode = 0;
    if (first != null){
      hashCode += hashCode(first);
    }
    if (second != null){
      hashCode += hashCode(second);
    }
    if (third != null){
      hashCode += hashCode(third);
    }
    return hashCode;
  }

  private static int hashCode(final Object o) {
    return o instanceof Object[] ? Arrays.hashCode((Object[])o) : o.hashCode();
  }

  @Override
  public String toString() {
    return "<" + first + "," + second + ","+third+">";
  }
}
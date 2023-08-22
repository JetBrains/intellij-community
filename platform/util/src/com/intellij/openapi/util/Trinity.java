// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * <h3>Obsolescence notice</h3>
 * <p>
 *   Define a proper Kotlin data class or Java record instead.
 * </p>
 */
@Obsolete
public class Trinity<A, B, C> {
  public final A first;
  public final B second;
  public final C third;

  public Trinity(A first, B second, C third) {
    this.first = first;
    this.second = second;
    this.third = third;
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

  @Contract(value = "_, _, _ -> new", pure = true)
  public static <A, B, C> @NotNull Trinity<A, B, C> create(A first, B second, C third) {
    return new Trinity<>(first, second, third);
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
           && Comparing.equal(first, ((Trinity<?,?,?>)o).first)
           && Comparing.equal(second, ((Trinity<?,?,?>)o).second)
           && Comparing.equal(third, ((Trinity<?,?,?>)o).third);
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

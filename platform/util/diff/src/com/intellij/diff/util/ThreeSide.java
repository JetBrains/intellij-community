// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.util.Function;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public enum ThreeSide {
  LEFT(0),
  BASE(1),
  RIGHT(2);

  private final int myIndex;

  ThreeSide(int index) {
    myIndex = index;
  }

  @NotNull
  public static ThreeSide fromIndex(int index) {
    if (index == 0) return LEFT;
    if (index == 1) return BASE;
    if (index == 2) return RIGHT;
    throw new IndexOutOfBoundsException("index: " + index);
  }

  public int getIndex() {
    return myIndex;
  }

  //
  // Helpers
  //

  @Nullable
  @Contract(value = "!null, !null, !null -> !null; null, null, null -> null", pure = true)
  public <T> T select(@Nullable T left, @Nullable T base, @Nullable T right) {
    if (myIndex == 0) return left;
    if (myIndex == 1) return base;
    if (myIndex == 2) return right;
    throw new IllegalStateException();
  }

  @NotNull
  @Contract(pure = true)
  public <T> T selectNotNull(@NotNull T left, @NotNull T base, @NotNull T right) {
    if (myIndex == 0) return left;
    if (myIndex == 1) return base;
    if (myIndex == 2) return right;
    throw new IllegalStateException();
  }

  @Contract(pure = true)
  public int select(int left, int base, int right) {
    if (myIndex == 0) return left;
    if (myIndex == 1) return base;
    if (myIndex == 2) return right;
    throw new IllegalStateException();
  }

  @Contract(pure = true)
  public int select(int @NotNull [] array) {
    assert array.length == 3;
    return array[myIndex];
  }

  @Contract(pure = true)
  public <T> T select(T @NotNull [] array) {
    assert array.length == 3;
    return array[myIndex];
  }

  @NotNull
  @Contract(pure = true)
  public <T> T selectNotNull(T @NotNull [] array) {
    assert array.length == 3;
    return array[myIndex];
  }

  @Contract(pure = true)
  public <T> T select(@NotNull List<T> list) {
    assert list.size() == 3;
    return list.get(myIndex);
  }

  @NotNull
  @Contract(pure = true)
  public <T> T selectNotNull(@NotNull List<T> list) {
    assert list.size() == 3;
    return list.get(myIndex);
  }

  @Nullable
  @Contract(pure = true)
  public static <T> ThreeSide fromValue(@NotNull List<? extends T> list, @Nullable T value) {
    assert list.size() == 3;
    int index = list.indexOf(value);
    return index != -1 ? fromIndex(index) : null;
  }

  @NotNull
  public static <T> List<T> map(@NotNull Function<? super ThreeSide, ? extends T> function) {
    return Arrays.asList(function.fun(LEFT), function.fun(BASE), function.fun(RIGHT));
  }
}

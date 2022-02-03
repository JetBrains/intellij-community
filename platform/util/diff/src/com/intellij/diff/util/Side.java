// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.openapi.util.Couple;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public enum Side {
  LEFT(0),
  RIGHT(1);

  private final int myIndex;

  Side(int index) {
    myIndex = index;
  }

  @NotNull
  public static Side fromIndex(int index) {
    if (index == 0) return LEFT;
    if (index == 1) return RIGHT;
    throw new IndexOutOfBoundsException("index: " + index);
  }

  @NotNull
  public static Side fromLeft(boolean isLeft) {
    return isLeft ? LEFT : RIGHT;
  }

  @NotNull
  public static Side fromRight(boolean isRight) {
    return isRight ? RIGHT : LEFT;
  }

  public int getIndex() {
    return myIndex;
  }

  public boolean isLeft() {
    return myIndex == 0;
  }

  @NotNull
  @Contract(pure = true)
  public Side other() {
    return isLeft() ? RIGHT : LEFT;
  }

  @NotNull
  @Contract(pure = true)
  public Side other(boolean other) {
    return other ? other() : this;
  }

  //
  // Helpers
  //

  public int select(int left, int right) {
    return isLeft() ? left : right;
  }

  @Nullable
  @Contract(value = "!null, !null -> !null; null, null -> null", pure = true)
  public <T> T select(@Nullable T left, @Nullable T right) {
    return isLeft() ? left : right;
  }

  @NotNull
  @Contract(pure = true)
  public <T> T selectNotNull(@NotNull T left, @NotNull T right) {
    return isLeft() ? left : right;
  }

  @Contract(pure = true)
  public boolean select(boolean @NotNull [] array) {
    assert array.length == 2;
    return array[myIndex];
  }

  @Contract(pure = true)
  public int select(int @NotNull [] array) {
    assert array.length == 2;
    return array[myIndex];
  }

  @Contract(pure = true)
  public <T> T select(T @NotNull [] array) {
    assert array.length == 2;
    return array[myIndex];
  }

  @NotNull
  @Contract(pure = true)
  public <T> T selectNotNull(T @NotNull [] array) {
    assert array.length == 2;
    return array[myIndex];
  }

  @Contract(pure = true)
  public <T> T select(@NotNull List<T> list) {
    assert list.size() == 2;
    return list.get(myIndex);
  }

  @NotNull
  @Contract(pure = true)
  public <T> T selectNotNull(@NotNull List<T> list) {
    assert list.size() == 2;
    return list.get(myIndex);
  }

  @Contract(pure = true)
  public <T> T select(@NotNull Couple<T> region) {
    return isLeft() ? region.first : region.second;
  }

  @NotNull
  @Contract(pure = true)
  public <T> T selectNotNull(@NotNull Couple<T> region) {
    return isLeft() ? region.first : region.second;
  }

  @Nullable
  @Contract(pure = true)
  public static <T> Side fromValue(@NotNull List<? extends T> list, @Nullable T value) {
    assert list.size() == 2;
    int index = list.indexOf(value);
    return index != -1 ? fromIndex(index) : null;
  }

  //
  // Fragments
  //

  public int getStartOffset(@NotNull DiffFragment fragment) {
    return isLeft() ? fragment.getStartOffset1() : fragment.getStartOffset2();
  }

  public int getEndOffset(@NotNull DiffFragment fragment) {
    return isLeft() ? fragment.getEndOffset1() : fragment.getEndOffset2();
  }

  public int getStartLine(@NotNull LineFragment fragment) {
    return isLeft() ? fragment.getStartLine1() : fragment.getStartLine2();
  }

  public int getEndLine(@NotNull LineFragment fragment) {
    return isLeft() ? fragment.getEndLine1() : fragment.getEndLine2();
  }
}

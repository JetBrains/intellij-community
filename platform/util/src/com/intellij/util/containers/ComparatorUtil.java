// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.function.Function;

public final class ComparatorUtil {
  private ComparatorUtil() {
  }

  public static @NotNull <Type, Aspect> Comparator<Type> compareBy(final @NotNull Function<? super Type, ? extends Aspect> aspect, final @NotNull Comparator<? super Aspect> comparator) {
    return (element1, element2) -> comparator.compare(aspect.apply(element1), aspect.apply(element2));
  }

  public static @NotNull <T extends Comparable<? super T>> T max(@NotNull T o1, @NotNull T o2) {
    return o1.compareTo(o2) >= 0 ? o1 : o2;
  }

  public static @NotNull <T extends Comparable<? super T>> T min(@NotNull T o1, @NotNull T o2) {
    return o1.compareTo(o2) >= 0 ? o2 : o1;
  }

  public static <T> boolean equalsNullable(@Nullable T a, @Nullable T b) {
    if (a == null) {
      return b == null;
    }
    return a.equals(b);
  }
}

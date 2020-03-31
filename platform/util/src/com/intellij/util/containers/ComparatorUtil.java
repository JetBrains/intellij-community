/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.function.Function;

public class ComparatorUtil {
  private ComparatorUtil() {
  }

  @NotNull
  public static <Type, Aspect> Comparator<Type> compareBy(@NotNull final Function<? super Type, ? extends Aspect> aspect, @NotNull final Comparator<? super Aspect> comparator) {
    return (element1, element2) -> comparator.compare(aspect.apply(element1), aspect.apply(element2));
  }

  @NotNull
  public static <T extends Comparable<? super T>> T max(@NotNull T o1, @NotNull T o2) {
    return o1.compareTo(o2) >= 0 ? o1 : o2;
  }

  @NotNull
  public static <T extends Comparable<? super T>> T min(@NotNull T o1, @NotNull T o2) {
    return o1.compareTo(o2) >= 0 ? o2 : o1;
  }

  public static <T> boolean equalsNullable(@Nullable T a, @Nullable T b) {
    if (a == null) {
      return b == null;
    }
    return a.equals(b);
  }
}

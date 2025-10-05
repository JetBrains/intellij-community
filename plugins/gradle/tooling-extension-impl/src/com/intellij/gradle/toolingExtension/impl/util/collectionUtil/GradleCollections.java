// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.collectionUtil;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public final class GradleCollections {

  // Copy of com.intellij.util.containers.ContainerUtil.createMaybeSingletonList
  public static <T> @NotNull List<T> createMaybeSingletonList(@Nullable T element) {
    //noinspection SSBasedInspection
    return element == null ? Collections.emptyList() : Collections.singletonList(element);
  }

  public static List<String> mapToString(Iterable<?> collection) {
    return map(collection, it -> String.valueOf(it));
  }

  public static <T, R> List<R> map(Iterable<T> collection, Function<T, R> mapper) {
    List<R> result = new ArrayList<>();
    for (T item : collection) {
      result.add(mapper.apply(item));
    }
    return result;
  }

  public static <T> List<T> filter(Iterable<T> collection, Predicate<T> predicate) {
    List<T> result = new ArrayList<>();
    for (T item : collection) {
      if (predicate.test(item)) {
        result.add(item);
      }
    }
    return result;
  }
}

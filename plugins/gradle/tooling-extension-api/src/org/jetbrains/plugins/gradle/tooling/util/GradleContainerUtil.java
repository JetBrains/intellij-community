// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util;

import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public final class GradleContainerUtil {
  public static final ImmutableDomainObjectSet<?> EMPTY_DOMAIN_OBJECT_SET = ImmutableDomainObjectSet.of(Collections.emptyList());

  @Contract(pure = true)
  public static @NotNull <T> ImmutableDomainObjectSet<T> emptyDomainObjectSet() {
    //noinspection unchecked
    return (ImmutableDomainObjectSet<T>)EMPTY_DOMAIN_OBJECT_SET;
  }

  public static <T> boolean match(@NotNull Iterator<T> iterator1,
                                  @NotNull Iterator<T> iterator2,
                                  @NotNull BooleanBiFunction<? super T, ? super T> condition) {
    while (iterator2.hasNext()) {
      if (!iterator1.hasNext() || !condition.fun(iterator1.next(), iterator2.next())) {
        return false;
      }
    }
    return !iterator1.hasNext();
  }

  public static <T, R> R reduce(@NotNull Iterable<? extends T> iterable,
                                @Nullable R initialValue,
                                @NotNull BiFunction<? extends R, ? super R, T> function) {
    R currentResult = initialValue;
    for (T e : iterable) {
      currentResult = function.fun(currentResult, e);
    }
    return currentResult;
  }

  @Contract("!null -> !null; null -> null")
  public static Set<File> unmodifiableFileSet(@Nullable Collection<String> paths) {
    if (paths == null) return null;
    if (paths.isEmpty()) return Collections.emptySet();
    LinkedHashSet<File> files = new LinkedHashSet<>(paths.size());
    for (String path : paths) {
      if (path != null) {
        files.add(new File(path));
      }
    }
    return Collections.unmodifiableSet(files);
  }

  @Contract("!null -> !null; null -> null")
  public static Set<String> unmodifiablePathSet(@Nullable Collection<File> files) {
    if (files == null) return null;
    if (files.isEmpty()) return Collections.emptySet();
    LinkedHashSet<String> paths = new LinkedHashSet<>(files.size());
    for (File file : files) {
      if (file != null) {
        paths.add(file.getPath());
      }
    }
    return Collections.unmodifiableSet(paths);
  }
}

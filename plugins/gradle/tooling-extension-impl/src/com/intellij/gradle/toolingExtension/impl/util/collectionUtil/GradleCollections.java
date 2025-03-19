// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.collectionUtil;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class GradleCollections {

  // Copy of com.intellij.util.containers.ContainerUtil.createMaybeSingletonList
  public static <T> @NotNull List<T> createMaybeSingletonList(@Nullable T element) {
    //noinspection SSBasedInspection
    return element == null ? Collections.emptyList() : Collections.singletonList(element);
  }
}

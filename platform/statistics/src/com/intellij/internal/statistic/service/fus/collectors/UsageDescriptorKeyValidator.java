// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors;

import org.jetbrains.annotations.NotNull;

public final class UsageDescriptorKeyValidator {
  public static final String FORBIDDEN_PATTERN = "[\\n]+";
  public static final String FORBIDDEN_PATTERN_REPLACEMENT = "\\n";

  public static @NotNull String replaceForbiddenSymbols(@NotNull String key) {
    return key.replaceAll(FORBIDDEN_PATTERN, FORBIDDEN_PATTERN_REPLACEMENT);
  }

  public static @NotNull String ensureProperKey(@NotNull String key) {
    return key;
  }
}

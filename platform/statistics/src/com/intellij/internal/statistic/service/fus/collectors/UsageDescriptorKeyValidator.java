// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import org.jetbrains.annotations.NotNull;

public final class UsageDescriptorKeyValidator {
  public static final String FORBIDDEN_PATTERN = "[\\n]+";
  public static final String FORBIDDEN_PATTERN_REPLACEMENT = "\\n";

  @NotNull
  public static String replaceForbiddenSymbols(@NotNull String key) {
    return key.replaceAll(FORBIDDEN_PATTERN, FORBIDDEN_PATTERN_REPLACEMENT);
  }

  @NotNull
  public static String ensureProperKey(@NotNull String key) {
    return key;
  }
}

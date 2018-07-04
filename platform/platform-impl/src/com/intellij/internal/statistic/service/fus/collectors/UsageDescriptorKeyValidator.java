// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import org.jetbrains.annotations.NotNull;

public class UsageDescriptorKeyValidator {
  public static final String FORBIDDEN_PATTERN = "[,\\s\\n]+";
  public static final String FORBIDDEN_PATTERN_REPLACEMENT = "[??]";

  @NotNull
  public static String replaceForbiddenSymbols(@NotNull String key) {
    return key.replaceAll(FORBIDDEN_PATTERN, FORBIDDEN_PATTERN_REPLACEMENT);
  }

  @NotNull
  public static String ensureProperKey(@NotNull String key) {
    return key.replaceAll(FORBIDDEN_PATTERN, ".");
  }
}

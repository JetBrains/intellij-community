// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public abstract class FeatureUsagesCollector {
  private static final String GROUP_ID_PATTERN = "([a-zA-Z]*\\.)*[a-zA-Z]*";

  public final boolean isValid() {
    return Pattern.compile(GROUP_ID_PATTERN).matcher(getGroupId()).matches();
  }

  @NotNull
  public abstract String getGroupId();
}

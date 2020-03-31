// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.persistence;

import org.jetbrains.annotations.NotNull;

public class WhitelistPathSettings {
  @NotNull private final String myCustomPath;
  private final boolean myUseCustomPath;

  public WhitelistPathSettings(@NotNull String customPath, boolean useCustomPath) {
    myCustomPath = customPath;
    myUseCustomPath = useCustomPath;
  }

  @NotNull
  public String getCustomPath() {
    return myCustomPath;
  }

  public boolean isUseCustomPath() {
    return myUseCustomPath;
  }
}

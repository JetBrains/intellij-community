// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage.persistence;

import org.jetbrains.annotations.NotNull;

public class EventsSchemePathSettings {
  private final @NotNull String myCustomPath;
  private final boolean myUseCustomPath;

  public EventsSchemePathSettings(@NotNull String customPath, boolean useCustomPath) {
    myCustomPath = customPath;
    myUseCustomPath = useCustomPath;
  }

  public @NotNull String getCustomPath() {
    return myCustomPath;
  }

  public boolean isUseCustomPath() {
    return myUseCustomPath;
  }
}

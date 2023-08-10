// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ConfigurationErrorType {

  private final boolean myCanIgnore;

  public ConfigurationErrorType(boolean canIgnore) {
    myCanIgnore = canIgnore;
  }

  public abstract @Nls @NotNull String getErrorText(int errorCount, @NlsSafe String firstElementName);

  public final boolean canIgnore() {
    return myCanIgnore;
  }

  public @Nullable @NonNls String getFeatureType() {
    return null;
  }
}

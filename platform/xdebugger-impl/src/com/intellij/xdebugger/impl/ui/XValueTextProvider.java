// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public interface XValueTextProvider {
  @Nullable
  String getValueText();

  @ApiStatus.Experimental
  boolean shouldShowTextValue();
}
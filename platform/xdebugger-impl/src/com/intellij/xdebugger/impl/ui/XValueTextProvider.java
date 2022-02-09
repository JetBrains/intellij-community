// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public interface XValueTextProvider {
  @Nullable
  String getValueText();

  @ApiStatus.Experimental
  boolean shouldShowTextValue();
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Internal
public class CustomizedBreakpointPresentation {
  private Icon myIcon;
  private String myErrorMessage;
  private volatile long myTimestamp; // for statistics

  public void setIcon(@Nullable Icon icon) {
    myIcon = icon;
  }

  public void setErrorMessage(@Nullable String errorMessage) {
    myErrorMessage = errorMessage;
  }

  public @Nullable Icon getIcon() {
    return myIcon;
  }

  public @Nullable String getErrorMessage() {
    return myErrorMessage;
  }

  public long getTimestamp() {
    return myTimestamp;
  }

  public void setTimestamp(long timestamp) {
    myTimestamp = timestamp;
  }
}

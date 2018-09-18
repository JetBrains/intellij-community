// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
*/
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

  @Nullable 
  public Icon getIcon() {
    return myIcon;
  }

  @Nullable
  public String getErrorMessage() {
    return myErrorMessage;
  }

  public long getTimestamp() {
    return myTimestamp;
  }

  public void setTimestamp(long timestamp) {
    myTimestamp = timestamp;
  }
}

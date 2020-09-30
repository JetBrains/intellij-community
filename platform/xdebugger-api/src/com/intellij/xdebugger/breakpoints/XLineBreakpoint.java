// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.xdebugger.breakpoints;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

/**
 * Represent a breakpoint which is set on some line in a file. This interface isn't supposed to be implemented by a plugin. In order to
 * support breakpoint provide {@link XLineBreakpointType} implementation
 */
public interface XLineBreakpoint<P extends XBreakpointProperties> extends XBreakpoint<P> {
  XLineBreakpoint<?>[] EMPTY_ARRAY = new XLineBreakpoint[0];

  int getLine();

  @NlsSafe
  String getFileUrl();

  @NlsSafe
  String getPresentableFilePath();

  @Override
  @NotNull
  XLineBreakpointType<P> getType();

  @NlsSafe
  String getShortFilePath();

  boolean isTemporary();

  void setTemporary(boolean temporary);
}

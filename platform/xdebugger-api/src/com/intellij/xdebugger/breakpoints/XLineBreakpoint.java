// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xdebugger.breakpoints;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represent a breakpoint which is set on some line in a file. This interface isn't supposed to be implemented by a plugin. In order to
 * support breakpoint provide {@link XLineBreakpointType} implementation
 */
@ApiStatus.NonExtendable
public interface XLineBreakpoint<P extends XBreakpointProperties> extends XBreakpoint<P> {
  XLineBreakpoint<?>[] EMPTY_ARRAY = new XLineBreakpoint[0];

  int getLine();

  @NlsSafe
  String getFileUrl();

  /**
   * Short path describing the breakpoint's file location (e.g., just a file name).
   * Should not be used to locate the actual file.
   */
  @NlsSafe
  String getShortFilePath();

  /**
   * Some kind of path or URL to the breakpoint's file location for showing in UI.
   * Might be shortened for better UX, should not be used to locate the actual file.
   */
  @NlsSafe
  String getPresentableFilePath();

  @Override
  @NotNull
  XLineBreakpointType<P> getType();

  boolean isTemporary();

  void setTemporary(boolean temporary);
}

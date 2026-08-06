// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XLineBreakpointVerticalPlacement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@Tag("line-breakpoint")
@ApiStatus.Internal
public class LineBreakpointState extends BreakpointState {
  private String myFileUrl;
  private int myLine;
  private @NotNull XLineBreakpointVerticalPlacement myPlacement = XLineBreakpointVerticalPlacement.ON_LINE;

  public LineBreakpointState() {
  }

  public LineBreakpointState(final boolean enabled, final String typeId, final String fileUrl, final int line, boolean temporary,
                             final long timeStamp, final SuspendPolicy suspendPolicy) {
    this(enabled, typeId, fileUrl, line, temporary, XLineBreakpointVerticalPlacement.ON_LINE, timeStamp, suspendPolicy);
  }

  public LineBreakpointState(final boolean enabled, final String typeId, final String fileUrl, final int line, boolean temporary,
                             final @NotNull XLineBreakpointVerticalPlacement placement, final long timeStamp, final SuspendPolicy suspendPolicy) {
    super(enabled, typeId, timeStamp, suspendPolicy);
    myFileUrl = fileUrl;
    myLine = line;
    setTemporary(temporary);
    myPlacement = placement;
  }

  @Tag("url")
  public String getFileUrl() {
    return myFileUrl;
  }

  public void setFileUrl(final String fileUrl) {
    myFileUrl = fileUrl;
  }

  @Tag("line")
  public int getLine() {
    return myLine;
  }

  public void setLine(final int line) {
    myLine = line;
  }

  @Tag("placement")
  public @NotNull XLineBreakpointVerticalPlacement getPlacement() {
    return myPlacement;
  }

  public void setPlacement(XLineBreakpointVerticalPlacement placement) {
    myPlacement = placement == null ? XLineBreakpointVerticalPlacement.ON_LINE : placement;
  }
}

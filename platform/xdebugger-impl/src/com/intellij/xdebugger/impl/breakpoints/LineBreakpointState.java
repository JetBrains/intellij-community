// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@Tag("line-breakpoint")
@ApiStatus.Internal
public class LineBreakpointState<P extends XBreakpointProperties> extends BreakpointState<XLineBreakpoint<P>, P, XLineBreakpointType<P>> {
  private String myFileUrl;
  private int myLine;
  private boolean myTemporary;

  public LineBreakpointState() {
  }

  public LineBreakpointState(final boolean enabled, final String typeId, final String fileUrl, final int line, boolean temporary,
                             final long timeStamp, final SuspendPolicy suspendPolicy) {
    super(enabled, typeId, timeStamp, suspendPolicy);
    myFileUrl = fileUrl;
    myLine = line;
    myTemporary = temporary;
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

  public boolean isTemporary() {
    return myTemporary;
  }

  public void setTemporary(boolean temporary) {
    myTemporary = temporary;
  }

  @Override
  public XBreakpointBase<XLineBreakpoint<P>,P, ?> createBreakpoint(final @NotNull XLineBreakpointType<P> type, @NotNull XBreakpointManagerImpl breakpointManager) {
    return new XLineBreakpointImpl<>(type, breakpointManager, this);
  }
}

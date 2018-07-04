/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.xdebugger.breakpoints;

import com.intellij.openapi.util.UserDataHolder;
import com.intellij.pom.Navigatable;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a breakpoint. This interface isn't supposed to be implemented by a plugin. In order to support breakpoint provide
 * {@link XBreakpointType} or {@link XLineBreakpointType} implementation
 *
 * @author nik
 * @see XLineBreakpoint
 * @see XBreakpointManager
 */
public interface XBreakpoint<P extends XBreakpointProperties> extends UserDataHolder {

  boolean isEnabled();

  void setEnabled(boolean enabled);

  @NotNull
  XBreakpointType<?, P> getType();

  P getProperties();

  @Nullable
  XSourcePosition getSourcePosition();

  @Nullable
  Navigatable getNavigatable();

  @NotNull
  SuspendPolicy getSuspendPolicy();

  void setSuspendPolicy(@NotNull SuspendPolicy policy);

  boolean isLogMessage();

  void setLogMessage(boolean logMessage);

  boolean isLogStack();

  void setLogStack(boolean logStack);

  /**
   * @deprecated use {@link #getLogExpressionObject()} instead
   */
  @Deprecated
  @Nullable
  String getLogExpression();

  void setLogExpression(@Nullable String expression);

  @Nullable
  XExpression getLogExpressionObject();

  void setLogExpressionObject(@Nullable XExpression expression);

  /**
   * @deprecated use {@link #getConditionExpression()} instead
   */
  @Deprecated
  @Nullable
  String getCondition();

  void setCondition(@Nullable String condition);

  @Nullable
  XExpression getConditionExpression();

  void setConditionExpression(@Nullable XExpression condition);

  long getTimeStamp();
}

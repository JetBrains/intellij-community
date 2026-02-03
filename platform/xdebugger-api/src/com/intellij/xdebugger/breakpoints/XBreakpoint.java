// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xdebugger.breakpoints;

import com.intellij.openapi.util.UserDataHolder;
import com.intellij.pom.Navigatable;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a breakpoint. This interface isn't supposed to be implemented by a plugin. In order to support breakpoint provide
 * {@link XBreakpointType} or {@link XLineBreakpointType} implementation
 *
 * @see XLineBreakpoint
 * @see XBreakpointManager
 */
@ApiStatus.NonExtendable
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

  void setLogExpression(@Nullable String expression);

  @Nullable
  XExpression getLogExpressionObject();

  void setLogExpressionObject(@Nullable XExpression expression);

  void setCondition(@Nullable String condition);

  @Nullable
  XExpression getConditionExpression();

  void setConditionExpression(@Nullable XExpression condition);

  long getTimeStamp();
}

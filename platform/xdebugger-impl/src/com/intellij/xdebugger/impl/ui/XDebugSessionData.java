/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.xdebugger.XExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XDebugSessionData {
  public static final DataKey<XDebugSessionData> DATA_KEY = DataKey.create("XDebugSessionData");

  @NotNull
  private XExpression[] myWatchExpressions;
  private final String myConfigurationName;
  private boolean myBreakpointsMuted = false;

  public XDebugSessionData(@NotNull XExpression[] watchExpressions, @NotNull String configurationName) {
    myWatchExpressions = watchExpressions;
    myConfigurationName = configurationName;
  }

  public void setWatchExpressions(@NotNull XExpression[] watchExpressions) {
    myWatchExpressions = watchExpressions;
  }

  @NotNull
  public XExpression[] getWatchExpressions() {
    return myWatchExpressions;
  }

  public boolean isBreakpointsMuted() {
    return myBreakpointsMuted;
  }

  public void setBreakpointsMuted(boolean breakpointsMuted) {
    myBreakpointsMuted = breakpointsMuted;
  }

  @NotNull
  public String getConfigurationName() {
    return myConfigurationName;
  }
}

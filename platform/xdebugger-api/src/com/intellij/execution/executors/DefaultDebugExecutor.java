/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution.executors;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.UIBundle;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author spleaner
 */
public class DefaultDebugExecutor extends Executor {
  @NonNls public static final String EXECUTOR_ID = ToolWindowId.DEBUG;
  private final String myStartActionText = XDebuggerBundle.message("debugger.runner.start.action.text");
  private final String myDescription = XDebuggerBundle.message("string.debugger.runner.description");

  public String getToolWindowId() {
    return ToolWindowId.DEBUG;
  }

  public Icon getToolWindowIcon() {
    return AllIcons.Toolwindows.ToolWindowDebugger;
  }

  @NotNull
  public Icon getIcon() {
    return AllIcons.Actions.StartDebugger;
  }

  public Icon getDisabledIcon() {
    return AllIcons.Process.DisabledDebug;
  }

  @NotNull
  public String getActionName() {
    return UIBundle.message("tool.window.name.debug");
  }

  @NotNull
  public String getId() {
    return EXECUTOR_ID;
  }

  public String getContextActionId() {
    return "DebugClass";
  }

  @NotNull
  public String getStartActionText() {
    return myStartActionText;
  }

  public String getDescription() {
    return myDescription;
  }

  public String getHelpId() {
    return "debugging.DebugWindow";
  }

  public static Executor getDebugExecutorInstance() {
    return ExecutorRegistry.getInstance().getExecutorById(EXECUTOR_ID);
  }
}

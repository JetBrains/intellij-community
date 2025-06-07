// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.executors;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.UIBundle;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DefaultDebugExecutor extends Executor {
  public static final @NonNls String EXECUTOR_ID = ToolWindowId.DEBUG;

  @Override
  public @NotNull String getToolWindowId() {
    return ToolWindowId.DEBUG;
  }

  @Override
  public @NotNull Icon getToolWindowIcon() {
    return AllIcons.Toolwindows.ToolWindowDebugger;
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.Actions.StartDebugger;
  }

  @Override
  public @NotNull Icon getRerunIcon() {
    return AllIcons.Actions.RestartDebugger;
  }

  @Override
  public Icon getDisabledIcon() {
    return IconLoader.getDisabledIcon(getIcon());
  }

  @Override
  public @NotNull String getActionName() {
    return UIBundle.message("tool.window.name.debug");
  }

  @Override
  public @NotNull String getId() {
    return EXECUTOR_ID;
  }

  @Override
  public String getContextActionId() {
    return "DebugClass";
  }

  @Override
  public @NotNull String getStartActionText() {
    return XDebuggerBundle.message("debugger.runner.start.action.text");
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getStartActionText(@NotNull String configurationName) {
    if (configurationName.isEmpty()) return getStartActionText();
    return TextWithMnemonic.parse(XDebuggerBundle.message("debugger.runner.start.action.text.2"))
      .replaceFirst("%s", shortenNameIfNeeded(configurationName)).toString();
  }

  @Override
  public String getDescription() {
    return XDebuggerBundle.message("string.debugger.runner.description");
  }

  @Override
  public String getHelpId() {
    return "debugging.DebugWindow";
  }

  @Override
  public boolean isSupportedOnTarget() {
    return EXECUTOR_ID.equalsIgnoreCase(getId());
  }

  public static Executor getDebugExecutorInstance() {
    return ExecutorRegistry.getInstance().getExecutorById(EXECUTOR_ID);
  }
}

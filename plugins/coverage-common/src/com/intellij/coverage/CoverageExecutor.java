// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.execution.Executor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CoverageExecutor extends Executor {

  public static final @NonNls String EXECUTOR_ID = "Coverage";

  @Override
  public @NotNull String getStartActionText() {
   return CoverageBundle.message("run.with.coverage");
  }

  @Override
  public @NotNull String getStartActionText(@NotNull String configurationName) {
    if (configurationName.isEmpty()) return getStartActionText();
    String configName = shortenNameIfNeeded(configurationName);
    return TextWithMnemonic.parse(CoverageBundle.message("run.with.coverage.mnemonic")).replaceFirst("%s", configName).toString();
  }

  @Override
  public @NotNull String getToolWindowId() {
    return ToolWindowId.RUN;
  }

  @Override
  public @NotNull Icon getToolWindowIcon() {
    return AllIcons.Toolwindows.ToolWindowRunWithCoverage;
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.General.RunWithCoverage;
  }

  @Override
  public Icon getDisabledIcon() {
    return null;
  }

  @Override
  public String getDescription() {
    return CoverageBundle.message("run.selected.configuration.with.coverage.enabled");
  }

  @Override
  public @NotNull String getActionName() {
    return CoverageBundle.message("action.name.cover");
  }

  @Override
  public @NotNull String getId() {
    return EXECUTOR_ID;
  }

  @Override
  public String getContextActionId() {
    return "RunCoverage";
  }

  @Override
  public String getHelpId() {
    return null;//todo
  }

  @Override
  public boolean isSupportedOnTarget() {
    return EXECUTOR_ID.equalsIgnoreCase(getId());
  }
}

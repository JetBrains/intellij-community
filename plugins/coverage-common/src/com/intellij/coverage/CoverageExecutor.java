package com.intellij.coverage;

import com.intellij.execution.Executor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CoverageExecutor extends Executor {

  public static final @NonNls String EXECUTOR_ID = "Coverage";

  @Override
  @NotNull
  public String getStartActionText() {
   return CoverageBundle.message("run.with.coverage");
  }

  @NotNull
  @Override
  public String getStartActionText(@NotNull String configurationName) {
    String configName = StringUtil.isEmpty(configurationName) ? "" : " '" + shortenNameIfNeeded(configurationName) + "'";
    return TextWithMnemonic.parse(CoverageBundle.message("run.with.coverage.mnemonic")).replaceFirst("%s", configName).toString();
  }

  @NotNull
  @Override
  public String getToolWindowId() {
    return ToolWindowId.RUN;
  }

  @NotNull
  @Override
  public Icon getToolWindowIcon() {
    return AllIcons.General.RunWithCoverage;
  }

  @Override
  @NotNull
  public Icon getIcon() {
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
  @NotNull
  public String getActionName() {
    return CoverageBundle.message("action.name.cover");
  }

  @Override
  @NotNull
  public String getId() {
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

package com.intellij.coverage;

import com.intellij.execution.Executor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CoverageExecutor extends Executor {

  public static final String EXECUTOR_ID = "Coverage";

  @NotNull
  public String getStartActionText() {
    return "Run with Co_verage";
  }

  @Override
  public String getStartActionText(String configurationName) {
    final String name = configurationName != null ? escapeMnemonicsInConfigurationName(shortenNameIfNeed(configurationName)) : null;
    return "Run" + (StringUtil.isEmpty(name) ? "" :  " '" + name + "'") + " with Co_verage";
  }


  private static String escapeMnemonicsInConfigurationName(String configurationName) {
    return configurationName.replace("_", "__");
  }

  public String getToolWindowId() {
    return ToolWindowId.RUN;
  }

  public Icon getToolWindowIcon() {
    return AllIcons.General.RunWithCoverage;
  }

  @NotNull
  public Icon getIcon() {
    return AllIcons.General.RunWithCoverage;
  }

  public Icon getDisabledIcon() {
    return null;
  }

  public String getDescription() {
    return "Run selected configuration with coverage enabled";
  }

  @NotNull
  public String getActionName() {
    return "Cover";
  }

  @NotNull
  public String getId() {
    return EXECUTOR_ID;
  }

  public String getContextActionId() {
    return "RunCoverage";
  }

  public String getHelpId() {
    return null;//todo
  }
}

package com.intellij.coverage;

import com.intellij.execution.Executor;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CoverageExecutor extends Executor {

  private static final Icon TOOLWINDOW_ICON = IconLoader.getIcon("/general/runWithCoverage.png");
  private static final Icon DISABLED_ICON = IconLoader.getIcon("/general/runWithCoverage.png");
  private static final Icon ICON = DISABLED_ICON;

  public static final String EXECUTOR_ID = "Coverage";

  @NotNull
  public String getStartActionText() {
    return "Run with Co_verage";
  }

  @Override
  public String getStartActionText(String configurationName) {
    final String name = escapeMnemonicsInConfigurationName(StringUtil.first(configurationName, 30, true));
    return "Run" + (StringUtil.isEmpty(name) ? "" :  " '" + name + "'") + " with Co_verage";
  }


  private static String escapeMnemonicsInConfigurationName(String configurationName) {
    return configurationName.replace("_", "__");
  }

  public String getToolWindowId() {
    return ToolWindowId.RUN;
  }

  public Icon getToolWindowIcon() {
    return TOOLWINDOW_ICON;
  }

  @NotNull
  public Icon getIcon() {
    return ICON;
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

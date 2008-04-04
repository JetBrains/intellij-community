package com.intellij.lang.ant.validation;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.lang.ant.AntBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public abstract class AntInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return AntBundle.message("ant.inspections.display.name");
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  public boolean isEnabledByDefault() {
    return true;
  }
}

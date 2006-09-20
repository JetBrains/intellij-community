package com.intellij.lang.ant.validation;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.ant.AntBundle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public abstract class AntInspection extends LocalInspectionTool {

  protected static final LocalQuickFix[] EMPTY_FIXES = new LocalQuickFix[0]; 

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

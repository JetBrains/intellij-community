// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchInspectionToolWrapper extends LocalInspectionToolWrapper {

  public StructuralSearchInspectionToolWrapper(Configuration configuration) {
    super(new StructuralSearchFakeInspection(configuration));
  }

  private StructuralSearchInspectionToolWrapper(@NotNull LocalInspectionTool tool) {
    super(tool);
  }

  @NotNull
  @Override
  public LocalInspectionToolWrapper createCopy() {
    return new StructuralSearchInspectionToolWrapper(new StructuralSearchFakeInspection((StructuralSearchFakeInspection)getTool()));
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return getTool().getDisplayName();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public String getID() {
    return getTool().getID();
  }

  @NotNull
  @Override
  public String getGroupDisplayName() {
    return getTool().getGroupDisplayName();
  }

  public void setProfile(InspectionProfileImpl profile) {
    ((StructuralSearchFakeInspection)myTool).setProfile(profile);
  }
}

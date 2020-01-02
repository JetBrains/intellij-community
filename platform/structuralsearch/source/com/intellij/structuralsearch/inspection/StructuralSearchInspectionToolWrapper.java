// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.structuralsearch.inspection.highlightTemplate.SSBasedInspection;
import com.intellij.structuralsearch.inspection.highlightTemplate.StructuralSearchFakeInspection;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchInspectionToolWrapper extends LocalInspectionToolWrapper {
  StructuralSearchInspectionToolWrapper(Configuration configuration) {
    super(new StructuralSearchFakeInspection(configuration.getName(), configuration.getUuid()));
  }

  private StructuralSearchInspectionToolWrapper(@NotNull LocalInspectionTool tool) {
    super(tool);
  }

  @NotNull
  @Override
  public LocalInspectionToolWrapper createCopy() {
    return new StructuralSearchInspectionToolWrapper(new StructuralSearchFakeInspection((StructuralSearchFakeInspection)getTool()));
  }

  @Override
  public void initialize(@NotNull GlobalInspectionContext context) {
    super.initialize(context);
    final InspectionProfileImpl profile = ((GlobalInspectionContextBase)context).getCurrentProfile();
    final InspectionToolWrapper<?, ?> tool = profile.getInspectionTool(SSBasedInspection.SHORT_NAME, context.getProject());
    assert tool != null;
    final SSBasedInspection inspection = (SSBasedInspection)tool.getTool();
    inspection.setSessionProfile(profile);
  }

  public void setProfile(InspectionProfileImpl profile) {
    ((StructuralSearchFakeInspection)myTool).setProfile(profile);
  }
}

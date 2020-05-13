/*
 * Copyright 2006-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.modularization;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.ui.SingleIntegerFieldOptionsPanel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class ModuleWithTooFewClassesInspection extends BaseGlobalInspection {

  @SuppressWarnings({"PublicField"})
  public int limit = 10;

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity, @NotNull AnalysisScope analysisScope, @NotNull InspectionManager inspectionManager,
                                                           @NotNull GlobalInspectionContext globalInspectionContext) {
    if (!(refEntity instanceof RefModule)) {
      return null;
    }
    final RefModule refModule = (RefModule)refEntity;
    final List<RefEntity> children = refModule.getChildren();
    int numClasses = 0;
    for (RefEntity child : children) {
      if (child instanceof RefClass) {
        numClasses++;
      }
    }
    if (numClasses >= limit || numClasses == 0) {
      return null;
    }
    final Project project = globalInspectionContext.getProject();
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length == 1) {
      return null;
    }
    final String errorString = InspectionGadgetsBundle.message("module.with.too.few.classes.problem.descriptor",
                                                               refModule.getName(), Integer.valueOf(numClasses), Integer.valueOf(limit));
    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(errorString)
    };
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel(InspectionGadgetsBundle.message("module.with.too.few.classes.min.option"), this, "limit");
  }
}

/*
 * Copyright 2006-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.packaging;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.PackageGlobalInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.number;
import static com.intellij.codeInspection.options.OptPane.pane;

public class PackageWithTooManyClassesInspection extends PackageGlobalInspection {

  @SuppressWarnings("PublicField")
  public int limit = 50;

  @Override
  public CommonProblemDescriptor @Nullable [] checkPackage(@NotNull RefPackage refPackage,
                                                           @NotNull AnalysisScope analysisScope,
                                                           @NotNull InspectionManager inspectionManager,
                                                           @NotNull GlobalInspectionContext globalInspectionContext) {
    int numClasses = ReadAction.compute(() -> {
      final Project project = inspectionManager.getProject();
      final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(refPackage.getQualifiedName());
      if (aPackage == null) {
        return -1;
      }
      return aPackage.getClasses(GlobalSearchScope.allScope(project)).length;
    });
    if (numClasses <= limit) {
      return null;
    }
    final String errorString = InspectionGadgetsBundle.message("package.with.too.many.classes.problem.descriptor",
                                                               refPackage.getQualifiedName(), Integer.valueOf(numClasses),
                                                               Integer.valueOf(limit));
    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(errorString)
    };
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("limit", InspectionGadgetsBundle.message("package.with.too.many.classes.max.option"), 1, 1000));
  }
}

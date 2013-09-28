/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.javadoc;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class MissingPackageInfoInspection extends BaseGlobalInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("missing.package.info.display.name");
  }

  @Nullable
  @Override
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                @NotNull AnalysisScope scope,
                                                @NotNull InspectionManager manager,
                                                @NotNull GlobalInspectionContext globalContext) {
    if (!(refEntity instanceof RefPackage)) {
      return null;
    }
    final RefPackage refPackage = (RefPackage)refEntity;
    final String packageName = refPackage.getQualifiedName();
    final Project project = globalContext.getProject();
    final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
    if (aPackage == null) {
      return null;
    }
    final List<RefEntity> children = refPackage.getChildren();
    boolean hasClasses = false;
    for (RefEntity child : children) {
      if (child instanceof RefClass) {
        hasClasses = true;
        break;
      }
    }
    if (!hasClasses) {
      return null;
    }
    final PsiDirectory[] directories = aPackage.getDirectories();
    for (PsiDirectory directory : directories) {
      final boolean packageInfoFound = directory.findFile(PsiPackage.PACKAGE_INFO_FILE) != null;
      final boolean packageDotHtmlFound = directory.findFile("package.html") != null;
      if (packageInfoFound || packageDotHtmlFound) {
        return null;
      }
    }
    if (PsiUtil.isLanguageLevel5OrHigher(aPackage)) {
      return new CommonProblemDescriptor[] {
        manager.createProblemDescriptor(InspectionGadgetsBundle.message("missing.package.info.problem.descriptor", packageName))};
    }
    else {
      return new CommonProblemDescriptor[] {
        manager.createProblemDescriptor(InspectionGadgetsBundle.message("missing.package.html.problem.descriptor", packageName))};
    }
  }
}

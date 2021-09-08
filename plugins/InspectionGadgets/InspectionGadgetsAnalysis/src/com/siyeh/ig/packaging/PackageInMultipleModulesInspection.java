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
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.reference.RefJavaManager;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PackageInMultipleModulesInspection extends BaseGlobalInspection {

  @Override
  public boolean isGraphNeeded() {
    return false;
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope,
                            @NotNull InspectionManager manager,
                            @NotNull GlobalInspectionContext globalContext,
                            @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    final Set<String> packages = new HashSet<>();
    scope.accept(file -> {
      if (file.isDirectory()) return true;
      final String packageName = ReadAction.compute(() -> {
        final PsiFile element = PsiManager.getInstance(scope.getProject()).findFile(file);
        if (!(element instanceof PsiClassOwner)) return null;
        final PsiClassOwner classOwner = (PsiClassOwner)element;
        return classOwner.getPackageName();
      });
      if (packageName == null || !packages.add(packageName)) return true;
      final RefPackage aPackage = (RefPackage)globalContext.getRefManager().getReference(RefJavaManager.PACKAGE, packageName);
      if (aPackage == null) return true;
      CommonProblemDescriptor[] descriptors = checkPackage(aPackage, scope, manager, globalContext);
      if (descriptors != null) {
        problemDescriptionsProcessor.addProblemElement(aPackage, descriptors);
      }
      return true;
    });
  }

  public CommonProblemDescriptor @Nullable [] checkPackage(@NotNull RefPackage aPackage,
                                                           @NotNull AnalysisScope analysisScope,
                                                           @NotNull InspectionManager inspectionManager,
                                                           @NotNull GlobalInspectionContext globalInspectionContext) {
    final Project project = inspectionManager.getProject();
    final PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(aPackage.getQualifiedName());
    if (psiPackage == null)  return null;
    final PsiFile @NotNull [] files = ReadAction.compute(() -> psiPackage.getFiles(GlobalSearchScope.projectScope(project)));
    final Set<Module> modules = new HashSet<>();
    final ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    for (PsiFile file : files) {
      final Module module = index.getModuleForFile(file.getVirtualFile());
      modules.add(module);
    }
    final int moduleCount = modules.size();
    if (moduleCount <= 1) {
      return null;
    }
    final List<Module> moduleList = new ArrayList<>(modules);
    final String errorString;
    if (moduleCount == 2) {
      errorString = InspectionGadgetsBundle.message(
        "package.in.multiple.modules.problem.descriptor2", aPackage.getQualifiedName(), moduleList.get(0), moduleList.get(1));
    }
    else if (moduleCount == 3) {
      errorString = InspectionGadgetsBundle.message(
        "package.in.multiple.modules.problem.descriptor3", aPackage.getQualifiedName(),
        moduleList.get(0), moduleList.get(1), moduleList.get(2));
    }
    else {
      errorString = InspectionGadgetsBundle.message(
        "package.in.multiple.modules.problem.descriptor.many", aPackage.getQualifiedName(),
        moduleList.get(0), moduleList.get(1), moduleCount - 2);
    }

    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(errorString)};
  }
}

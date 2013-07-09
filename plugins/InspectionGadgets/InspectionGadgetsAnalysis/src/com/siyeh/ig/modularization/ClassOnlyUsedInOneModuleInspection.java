/*
 * Copyright 2011 Bas Leijdekkers
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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiIdentifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.dependency.DependencyUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class ClassOnlyUsedInOneModuleInspection extends BaseGlobalInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "class.only.used.in.one.module.display.name");
  }

  @Nullable
  @Override
  public CommonProblemDescriptor[] checkElement(
    @NotNull RefEntity refEntity,
    @NotNull AnalysisScope scope,
    @NotNull InspectionManager manager,
    @NotNull GlobalInspectionContext globalContext) {
    if (!(refEntity instanceof RefClass)) {
      return null;
    }
    final RefClass refClass = (RefClass)refEntity;
    final RefEntity owner = refClass.getOwner();
    if (!(owner instanceof RefPackage)) {
      return null;
    }
    final Set<RefClass> dependencies =
      DependencyUtils.calculateDependenciesForClass(refClass);
    RefModule otherModule = null;
    for (RefClass dependency : dependencies) {
      final RefModule module = dependency.getModule();
      if (refClass.getModule() == module) {
        return null;
      }
      if (otherModule != module) {
        if (otherModule == null) {
          otherModule = module;
        }
        else {
          return null;
        }
      }
    }
    final Set<RefClass> dependents =
      DependencyUtils.calculateDependentsForClass(refClass);
    for (RefClass dependent : dependents) {
      final RefModule module = dependent.getModule();
      if (refClass.getModule() == module) {
        return null;
      }
      if (otherModule != module) {
        if (otherModule == null) {
          otherModule = module;
        }
        else {
          return null;
        }
      }
    }
    if (otherModule == null) {
      return null;
    }
    final PsiClass aClass = refClass.getElement();
    final PsiIdentifier identifier = aClass.getNameIdentifier();
    if (identifier == null) {
      return null;
    }
    final String moduleName = otherModule.getName();
    return new CommonProblemDescriptor[]{
      manager.createProblemDescriptor(identifier,
                                      InspectionGadgetsBundle.message(
                                        "class.only.used.in.one.module.problem.descriptor",
                                        moduleName),
                                      true, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false)
    };
  }
}

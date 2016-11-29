/*
 * Copyright 2006-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.dependency;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class CyclicClassInitializationInspection extends BaseGlobalInspection {

  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "cyclic.class.initialization.display.name");
  }

  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(
    @NotNull RefEntity refEntity,
    @NotNull AnalysisScope analysisScope,
    @NotNull InspectionManager inspectionManager,
    @NotNull GlobalInspectionContext globalInspectionContext) {
    if (!(refEntity instanceof RefClass)) {
      return null;
    }
    final RefClass refClass = (RefClass)refEntity;
    final PsiClass aClass = refClass.getElement();
    if (aClass.getContainingClass() != null) {
      return null;
    }
    final Set<RefClass> dependencies =
      InitializationDependencyUtils.calculateTransitiveInitializationDependentsForClass(refClass);
    final Set<RefClass> dependents =
      InitializationDependencyUtils.calculateTransitiveInitializationDependenciesForClass(refClass);
    final Set<RefClass> mutualDependents = new HashSet<>(dependencies);
    mutualDependents.retainAll(dependents);

    final int numMutualDependents = mutualDependents.size();
    if (numMutualDependents == 0) {
      return null;
    }
    final String errorString = InspectionGadgetsBundle.message(
      "cyclic.class.initialization.problem.descriptor",
      refEntity.getName(), numMutualDependents);
    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(errorString)
    };
  }
}

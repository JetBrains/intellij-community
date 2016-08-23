/*
 * Copyright 2006-2015 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.util.RefEntityAlphabeticalComparator;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CyclicClassDependencyInspection extends BaseGlobalInspection {

  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("cyclic.class.dependency.display.name");
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
    if (aClass == null || aClass.getContainingClass() != null || aClass instanceof PsiAnonymousClass) {
      return null;
    }
    final Set<RefClass> dependencies = DependencyUtils.calculateTransitiveDependenciesForClass(refClass);
    final Set<RefClass> dependents = DependencyUtils.calculateTransitiveDependentsForClass(refClass);
    final Set<RefClass> mutualDependents = new HashSet<>(dependencies);
    mutualDependents.retainAll(dependents);
    final int numMutualDependents = mutualDependents.size();
    if (numMutualDependents == 0) {
      return null;
    }
    final String errorString;
    if (numMutualDependents == 1) {
      final RefClass[] classes = mutualDependents.toArray(new RefClass[1]);
      errorString = InspectionGadgetsBundle.message("cyclic.class.dependency.1.problem.descriptor",
                                                    refEntity.getName(), classes[0].getExternalName());
    }
    else if (numMutualDependents == 2) {
      final RefClass[] classes = mutualDependents.toArray(new RefClass[2]);
      Arrays.sort(classes, RefEntityAlphabeticalComparator.getInstance());
      errorString = InspectionGadgetsBundle.message("cyclic.class.dependency.2.problem.descriptor",
                                                    refEntity.getName(), classes[0].getExternalName(), classes[1].getExternalName());
    }
    else {
      errorString = InspectionGadgetsBundle.message("cyclic.class.dependency.problem.descriptor",
                                                    refEntity.getName(), Integer.valueOf(numMutualDependents));
    }
    final PsiElement anchor = aClass.getNameIdentifier();
    if (anchor == null) return null;
    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(anchor, errorString, (LocalQuickFix)null,
                                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false)
    };
  }
}

/*
 * Copyright 2006-2013 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.codeInspection.util.RefEntityAlphabeticalComparator;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CyclicPackageDependencyInspection extends BaseGlobalInspection {

  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "cyclic.package.dependency.display.name");
  }

  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(
    @NotNull RefEntity refEntity,
    @NotNull AnalysisScope analysisScope,
    @NotNull InspectionManager inspectionManager,
    @NotNull GlobalInspectionContext globalInspectionContext) {
    if (!(refEntity instanceof RefPackage)) {
      return null;
    }
    final RefPackage refPackage = (RefPackage)refEntity;
    final Set<RefPackage> dependencies = DependencyUtils.calculateTransitiveDependenciesForPackage(refPackage);
    final Set<RefPackage> dependents = DependencyUtils.calculateTransitiveDependentsForPackage(refPackage);
    final Set<RefPackage> mutualDependents = new HashSet<>(dependencies);
    mutualDependents.retainAll(dependents);
    final int numMutualDependents = mutualDependents.size();
    if (numMutualDependents == 0) {
      return null;
    }
    final String packageName = refPackage.getQualifiedName();
    final String errorString;
    if (numMutualDependents == 1) {
      final RefPackage[] packages = mutualDependents.toArray(new RefPackage[1]);
      errorString = InspectionGadgetsBundle.message("cyclic.package.dependency.1.problem.descriptor",
                                                    packageName, packages[0].getQualifiedName());
    }
    else if (numMutualDependents == 2) {
      final RefPackage[] packages = mutualDependents.toArray(new RefPackage[2]);
      Arrays.sort(packages, RefEntityAlphabeticalComparator.getInstance());
      errorString = InspectionGadgetsBundle.message("cyclic.package.dependency.2.problem.descriptor",
                                                    packageName, packages[0].getQualifiedName(), packages[1].getQualifiedName());
    }
    else {
      errorString = InspectionGadgetsBundle.message("cyclic.package.dependency.problem.descriptor",
                                                    packageName, Integer.valueOf(numMutualDependents));
    }
    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(errorString)
    };
  }
}

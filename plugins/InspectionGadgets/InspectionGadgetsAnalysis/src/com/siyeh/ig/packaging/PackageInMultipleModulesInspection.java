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
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.reference.RefPackage;
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
  public CommonProblemDescriptor @Nullable [] checkElement(
    @NotNull RefEntity refEntity, @NotNull AnalysisScope analysisScope,
    @NotNull InspectionManager inspectionManager,
    @NotNull GlobalInspectionContext globalInspectionContext) {
    if (!(refEntity instanceof RefPackage)) {
      return null;
    }
    final List<RefEntity> children = refEntity.getChildren();
    final Set<RefModule> modules = new HashSet<>();
    for (RefEntity child : children) {
      if (!(child instanceof RefClass)) {
        continue;
      }
      final RefClass refClass = (RefClass)child;
      final RefModule module = refClass.getModule();
      modules.add(module);
    }
    final int moduleCount = modules.size();
    if (moduleCount <= 1) {
      return null;
    }
    final List<RefModule> moduleList = new ArrayList<>(modules);
    final String errorString;
    if (moduleCount == 2) {
      errorString = InspectionGadgetsBundle.message(
        "package.in.multiple.modules.problem.descriptor2", refEntity.getQualifiedName(), moduleList.get(0), moduleList.get(1));
    }
    else if (moduleCount == 3) {
      errorString = InspectionGadgetsBundle.message(
        "package.in.multiple.modules.problem.descriptor3", refEntity.getQualifiedName(),
        moduleList.get(0), moduleList.get(1), moduleList.get(2));
    }
    else {
      errorString = InspectionGadgetsBundle.message(
        "package.in.multiple.modules.problem.descriptor.many", refEntity.getQualifiedName(),
        moduleList.get(0), moduleList.get(1), moduleCount - 2);
    }

    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(errorString)};
  }
}

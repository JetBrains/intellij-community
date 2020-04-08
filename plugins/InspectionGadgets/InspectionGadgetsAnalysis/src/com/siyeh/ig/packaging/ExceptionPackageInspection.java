/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class ExceptionPackageInspection extends BaseGlobalInspection {

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                           @NotNull AnalysisScope scope,
                                                           @NotNull InspectionManager manager,
                                                           @NotNull GlobalInspectionContext globalContext) {
    if (!(refEntity instanceof RefPackage)) {
      return null;
    }
    final List<RefEntity> children = refEntity.getChildren();
    boolean classSeen = false;
    for (RefEntity child : children) {
      if (!(child instanceof RefClass)) {
        continue;
      }
      final RefClass refClass = (RefClass)child;
      if (refClass.isTestCase()) {
        continue;
      }
      classSeen = true;
      UClass uClass = refClass.getUastElement();
      if (uClass == null) return null;
      PsiClass psiClass = uClass.getJavaPsi();
      if (!InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_THROWABLE)) {
        return null;
      }
    }
    if (!classSeen) {
      return null;
    }
    final String errorString = InspectionGadgetsBundle.message("exception.package.problem.descriptor", refEntity.getQualifiedName());
    return new CommonProblemDescriptor[]{manager.createProblemDescriptor(errorString)};
  }
}

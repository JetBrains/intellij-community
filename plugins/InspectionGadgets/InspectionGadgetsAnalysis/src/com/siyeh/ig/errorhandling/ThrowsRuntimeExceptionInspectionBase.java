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
package com.siyeh.ig.errorhandling;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ThrowsRuntimeExceptionInspectionBase extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("throws.runtime.exception.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("throws.runtime.exception.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowsRuntimeExceptionVisitor();
  }

  private static class ThrowsRuntimeExceptionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      final PsiReferenceList throwsList = method.getThrowsList();
      final PsiJavaCodeReferenceElement[] referenceElements = throwsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        final PsiElement target = referenceElement.resolve();
        if (!(target instanceof PsiClass)) {
          continue;
        }
        final PsiClass aClass = (PsiClass)target;
        if (!InheritanceUtil.isInheritor(aClass, "java.lang.RuntimeException")) {
          continue;
        }
        final String className = aClass.getName();
        registerError(referenceElement, className, referenceElement);
      }
    }
  }
}

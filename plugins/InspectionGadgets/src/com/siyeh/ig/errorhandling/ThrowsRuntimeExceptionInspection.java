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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ThrowsRuntimeExceptionInspection extends BaseInspection {
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
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ThrowsRuntimeExceptionFix((String) infos[0]);
  }

  private static class ThrowsRuntimeExceptionFix extends InspectionGadgetsFix {

    private final String myClassName;

    public ThrowsRuntimeExceptionFix(String className) {
      myClassName = className;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("throws.runtime.exception.quickfix", myClassName);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      descriptor.getPsiElement().delete();
    }
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
        registerError(referenceElement, className);
      }
    }
  }
}

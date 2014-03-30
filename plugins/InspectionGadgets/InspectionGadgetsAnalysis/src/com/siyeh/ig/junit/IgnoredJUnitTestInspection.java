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
package com.siyeh.ig.junit;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class IgnoredJUnitTestInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "ignored.junit.test.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiNamedElement info = (PsiNamedElement)infos[0];
    if (info instanceof PsiClass) {
      return InspectionGadgetsBundle.message(
        "ignored.junit.test.classproblem.descriptor",
        info.getName());
    }
    else {
      return InspectionGadgetsBundle.message(
        "ignored.junit.test.method.problem.descriptor",
        info.getName());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IgnoredJUnitTestVisitor();
  }

  private static class IgnoredJUnitTestVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
      super.visitAnnotation(annotation);
      final PsiModifierListOwner modifierListOwner =
        PsiTreeUtil.getParentOfType(annotation,
                                    PsiModifierListOwner.class);
      if (!(modifierListOwner instanceof PsiClass ||
            modifierListOwner instanceof PsiMethod)) {
        return;
      }
      final PsiJavaCodeReferenceElement nameReferenceElement =
        annotation.getNameReferenceElement();
      if (nameReferenceElement == null) {
        return;
      }
      final PsiElement target = nameReferenceElement.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      @NonNls final String qualifiedName = aClass.getQualifiedName();
      if (!"org.junit.Ignore".equals(qualifiedName)) {
        return;
      }
      registerError(annotation, modifierListOwner);
    }
  }
}

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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class StringEqualsCharSequenceInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.equals.char.sequence.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    return InspectionGadgetsBundle.message("string.equals.char.sequence.problem.descriptor", type.getPresentableText());
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiReferenceExpression expression = (PsiReferenceExpression)infos[1];
    if (PsiUtil.isLanguageLevel5OrHigher(expression) && !isStringEqualsCall(expression)) {
      return null;
    }
    return new StringEqualsCharSequenceFix();
  }

  private static boolean isStringEqualsCall(PsiReferenceExpression expression) {
    if (!"equals".equals(expression.getReferenceName())) {
      return false;
    }
    final PsiElement target = expression.resolve();
    if (!(target instanceof PsiMethod)) {
      return false;
    }
    final PsiMethod method = (PsiMethod)target;
    final PsiClass aClass = method.getContainingClass();
    return aClass != null && CommonClassNames.JAVA_LANG_STRING.equals(aClass.getQualifiedName());
  }

  private static class StringEqualsCharSequenceFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("string.equals.char.sequence.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiIdentifier identifier = JavaPsiFacade.getElementFactory(project).createIdentifier("contentEquals");
      element.replace(identifier);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringEqualsCharSequenceVisitor();
  }

  private static class StringEqualsCharSequenceVisitor extends BaseEqualsVisitor {

    @Override
    void checkTypes(@NotNull PsiReferenceExpression expression, @NotNull PsiType leftType, @NotNull PsiType rightType) {
      if (!leftType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return;
      }
      if (rightType.equalsToText(CommonClassNames.JAVA_LANG_STRING) || !InheritanceUtil.isInheritor(rightType, "java.lang.CharSequence")) {
        return;
      }
      final PsiElement name = expression.getReferenceNameElement();
      assert name != null;
      registerError(name, rightType, expression);
    }
  }
}

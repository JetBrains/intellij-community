/*
 * Copyright 2005-2007 Bas Leijdekkers
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
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RedundantMethodOverrideInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "redundant.method.override.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "redundant.method.override.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RedundantMethodOverrideFix();
  }

  private static class RedundantMethodOverrideFix
    extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "redundant.method.override.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiElement method = methodNameIdentifier.getParent();
      assert method != null;
      deleteElement(method);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantMethodOverrideVisitor();
  }

  private static class RedundantMethodOverrideVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      final Query<MethodSignatureBackedByPsiMethod> superMethodQuery =
        SuperMethodsSearch.search(method, null, true, false);
      final MethodSignatureBackedByPsiMethod signature =
        superMethodQuery.findFirst();
      if (signature == null) {
        return;
      }
      final PsiMethod superMethod = signature.getMethod();
      final PsiCodeBlock superBody = superMethod.getBody();
      if (superBody == null) {
        return;
      }
      final PsiModifierList superModifierList =
        superMethod.getModifierList();
      final PsiModifierList modifierList = method.getModifierList();
      if (!EquivalenceChecker.modifierListsAreEquivalent(
        modifierList, superModifierList)) {
        return;
      }
      final PsiType superReturnType = superMethod.getReturnType();
      if (superReturnType == null) {
        return;
      }
      final PsiType returnType = method.getReturnType();
      if (!superReturnType.equals(returnType)) {
        return;
      }
      if (!EquivalenceChecker.codeBlocksAreEquivalent(body, superBody)) {
        return;
      }
      registerMethodError(method);
    }
  }
}
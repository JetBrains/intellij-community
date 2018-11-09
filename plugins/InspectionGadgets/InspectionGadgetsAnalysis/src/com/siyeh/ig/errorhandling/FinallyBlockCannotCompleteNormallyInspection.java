/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public class FinallyBlockCannotCompleteNormallyInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "finally";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "finally.block.cannot.complete.normally.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "finally.block.cannot.complete.normally.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FinallyBlockCannotCompleteNormallyVisitor();
  }

  private static class FinallyBlockCannotCompleteNormallyVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitTryStatement(@NotNull PsiTryStatement statement) {
      super.visitTryStatement(statement);
      final PsiCodeBlock finallyBlock = statement.getFinallyBlock();
      if (finallyBlock == null) {
        return;
      }
      if (isInMainMethod(statement)) return;
      if (ControlFlowUtils.codeBlockMayCompleteNormally(finallyBlock)) {
        return;
      }
      final PsiElement[] children = statement.getChildren();
      for (final PsiElement child : children) {
        final String childText = child.getText();
        if (PsiKeyword.FINALLY.equals(childText)) {
          registerError(child);
          return;
        }
      }
    }
  }

  private static boolean isInMainMethod(@NotNull PsiStatement statement) {
    PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
    if (method == null) return false;
    if (!"main".equals(method.getName())) return false;
    PsiModifierList modifierList = method.getModifierList();
    if (!modifierList.hasExplicitModifier(PsiModifier.STATIC) ||
        !modifierList.hasExplicitModifier(PsiModifier.PUBLIC) ||
        method.getParameterList().getParametersCount() != 1) {
      return false;
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiParameter parameter = parameters[0];
    PsiArrayType arrayType = tryCast(parameter.getType(), PsiArrayType.class);
    if (arrayType == null) return false;
    PsiType componentType = arrayType.getDeepComponentType();
    return CommonClassNames.JAVA_LANG_STRING.equals(componentType.getCanonicalText());
  }
}
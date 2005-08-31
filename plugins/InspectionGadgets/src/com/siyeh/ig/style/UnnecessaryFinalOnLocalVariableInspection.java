/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RemoveModifierFix;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryFinalOnLocalVariableInspection extends MethodInspection {

  public String getGroupDisplayName() {
    return GroupNames.STYLE_GROUP_NAME;
  }

  public String buildErrorString(PsiElement location) {
    final PsiModifierList modifierList = (PsiModifierList)location.getParent();
    assert modifierList != null;
    final PsiVariable parameter = (PsiVariable)modifierList.getParent();
    assert parameter != null;
    final String parameterName = parameter.getName();
    return "Unnecessary #ref for variable " + parameterName + " #loc";
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryFinalOnLocalVariableVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return new RemoveModifierFix(location);
  }

  private static class UnnecessaryFinalOnLocalVariableVisitor extends BaseInspectionVisitor {

    public void visitDeclarationStatement(PsiDeclarationStatement statement) {
      super.visitDeclarationStatement(statement);
      final PsiElement[] declaredElements =
        statement.getDeclaredElements();
      if (declaredElements.length == 0) {
        return;
      }
      for (final PsiElement declaredElement : declaredElements) {
        if (!(declaredElement instanceof PsiLocalVariable)) {
          return;
        }
        final PsiLocalVariable variable = (PsiLocalVariable)declaredElement;
        if (!variable.hasModifierProperty(PsiModifier.FINAL)) {
          return;
        }
      }
      final PsiCodeBlock containingBlock =
        PsiTreeUtil.getParentOfType(statement, PsiCodeBlock.class);
      if (containingBlock == null) {
        return;
      }
      for (PsiElement declaredElement1 : declaredElements) {
        final PsiLocalVariable variable = (PsiLocalVariable)declaredElement1;
        if (VariableAccessUtils.variableIsUsedInInnerClass(variable, containingBlock)) {
          return;
        }
      }
      final PsiLocalVariable variable1 = (PsiLocalVariable)statement.getDeclaredElements()[0];
      registerModifierError(PsiModifier.FINAL, variable1);
    }

    public void visitTryStatement(@NotNull PsiTryStatement statement) {
      super.visitTryStatement(statement);
      final PsiCatchSection[] catchSections = statement.getCatchSections();
      for (PsiCatchSection catchSection : catchSections) {
        final PsiParameter parameter = catchSection.getParameter();
        final PsiCodeBlock catchBlock = catchSection.getCatchBlock();
        if (parameter == null || catchBlock == null) {
          continue;
        }
        if (parameter.hasModifierProperty(PsiModifier.FINAL) &&
            !VariableAccessUtils.variableIsUsedInInnerClass(parameter, catchBlock)) {
          registerModifierError(PsiModifier.FINAL, parameter);
        }
      }
    }

  }
}

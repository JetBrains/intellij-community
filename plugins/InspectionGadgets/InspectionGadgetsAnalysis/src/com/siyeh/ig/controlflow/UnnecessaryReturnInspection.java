/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteUnnecessaryStatementFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UnnecessaryReturnInspection extends BaseInspection {
  @SuppressWarnings("PublicField")
  public boolean ignoreInThenBranch = false;

  @Override
  @NotNull
  public String getID() {
    return "UnnecessaryReturnStatement";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.return.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    if (((Boolean)infos[0]).booleanValue()) {
      return InspectionGadgetsBundle.message("unnecessary.return.constructor.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("unnecessary.return.problem.descriptor");
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("unnecessary.return.option"), this, "ignoreInThenBranch");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new DeleteUnnecessaryStatementFix("return");
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return !FileTypeUtils.isInServerPageFile(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryReturnVisitor();
  }

  private class UnnecessaryReturnVisitor extends BaseInspectionVisitor {
    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      if (statement.getReturnValue() != null) {
        return;
      }
      final PsiElement methodParent = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class);
      PsiCodeBlock codeBlock = null;
      final boolean constructor;
      if (methodParent instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)methodParent;
        codeBlock = method.getBody();
        constructor = method.isConstructor();
      }
      else if (methodParent instanceof PsiLambdaExpression) {
        constructor = false;
        final PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)methodParent;
        final PsiElement lambdaBody = lambdaExpression.getBody();
        if (lambdaBody instanceof PsiCodeBlock) {
          codeBlock = (PsiCodeBlock)lambdaBody;
        }
      }
      else {
        return;
      }
      if (codeBlock == null) {
        return;
      }
      if (!ControlFlowUtils.blockCompletesWithStatement(codeBlock, statement)) {
        return;
      }
      if (ignoreInThenBranch && isInThenBranch(statement)) {
        return;
      }
      registerStatementError(statement, Boolean.valueOf(constructor));
    }

    private boolean isInThenBranch(PsiStatement statement) {
      final PsiIfStatement ifStatement =
        PsiTreeUtil.getParentOfType(statement, PsiIfStatement.class, true, PsiMethod.class, PsiLambdaExpression.class);
      if (ifStatement == null) {
        return false;
      }
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      return elseBranch != null && !PsiTreeUtil.isAncestor(elseBranch, statement, true);
    }
  }
}
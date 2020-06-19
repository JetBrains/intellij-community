/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.testFrameworks;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AssertWithoutMessageInspection extends BaseInspection {

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertionsWithoutMessagesVisitor();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assert.without.message.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  private static class AssertionsWithoutMessagesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      AssertHint assertHint = AssertHint.create(expression, methodName -> AssertHint.JUnitCommonAssertNames.ASSERT_METHOD_2_PARAMETER_COUNT.get(methodName));
      if (assertHint == null) {
        return;
      }
      PsiExpression message = assertHint.getMessage();
      if (message == null) {
        registerMethodCallError(expression, assertHint.isMessageOnFirstPosition());
      }
    }
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    return new InspectionGadgetsFix() {
      @Override
      protected void doFix(Project project, ProblemDescriptor descriptor) {
        Object firstArg = infos[0];
        if (!(firstArg instanceof Boolean)) return;
        boolean messageIsOnFirstPosition = (boolean)firstArg;

        PsiMethodCallExpression methodCallExpr = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethodCallExpression.class);
        if (methodCallExpr == null) return;

        PsiExpression newMessageExpr = JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText("\"\"", methodCallExpr);
        PsiExpressionList methodArgs = methodCallExpr.getArgumentList();
        PsiElement createdMessageExpr;
        if (messageIsOnFirstPosition) {
          PsiExpression[] methodArgExprs = methodArgs.getExpressions();
          PsiExpression firstMethodArgExpr = methodArgExprs.length > 0 ? methodArgExprs[0] : null;
          createdMessageExpr = methodArgs.addBefore(newMessageExpr, firstMethodArgExpr);
        }
        else {
          createdMessageExpr = methodArgs.add(newMessageExpr);
        }

        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) return;
        editor.getCaretModel().moveToOffset(createdMessageExpr.getTextOffset() + 1);
      }

      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return InspectionGadgetsBundle.message("assert.without.message.quick.fix.family.name");
      }
    };
  }
}
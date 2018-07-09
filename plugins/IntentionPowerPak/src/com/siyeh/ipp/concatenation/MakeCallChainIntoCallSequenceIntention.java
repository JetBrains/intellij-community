/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.concatenation;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.HighlightUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MakeCallChainIntoCallSequenceIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new MethodCallChainPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final List<String> callTexts = new ArrayList<>();
    PsiMethodCallExpression call = ObjectUtils.tryCast(element, PsiMethodCallExpression.class);
    PsiExpression root = MethodCallChainPredicate.getCallChainRoot(call);
    if (root == null) return;
    CommentTracker tracker = new CommentTracker();
    while (call != null && call != root) {
      callTexts.add(call.getMethodExpression().getReferenceName() + tracker.text(call.getArgumentList()));
      call = MethodCallUtils.getQualifierMethodCall(call);
    }
    final PsiType rootType = root.getType();
    if (rootType == null) return;
    final String targetText;
    final PsiStatement appendStatement;
    @NonNls final String firstStatement;
    final String variableDeclaration;
    final boolean showRenameTemplate;
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(element.getParent());
    if (parent instanceof PsiExpressionStatement) {
      targetText = root.getText();
      appendStatement = (PsiStatement)parent;
      firstStatement = null;
      variableDeclaration = null;
      showRenameTemplate = false;
    }
    else {
      final PsiElement grandParent = parent.getParent();
      appendStatement = (PsiStatement)grandParent;
      if (parent instanceof PsiAssignmentExpression && grandParent instanceof PsiExpressionStatement) {
        final PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
        final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignment.getLExpression());
        if (!(lhs instanceof PsiReferenceExpression)) {
          return;
        }
        final PsiReferenceExpression expression = (PsiReferenceExpression)lhs;
        final PsiElement target = expression.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        final PsiVariable variable = (PsiVariable)target;
        final PsiType variableType = variable.getType();
        if (variableType.equals(rootType)) {
          targetText = tracker.text(lhs);
          final PsiJavaToken token = assignment.getOperationSign();
          firstStatement = targetText + token.getText() + root.getText() + ';';
          showRenameTemplate = false;
          variableDeclaration = null;
        }
        else {
          targetText = "x";
          showRenameTemplate = true;
          final String firstAssignment = rootType.getCanonicalText() + ' ' + targetText + '=' + root.getText() + ';';
          firstStatement = (JavaCodeStyleSettings.getInstance(element.getContainingFile()).GENERATE_FINAL_LOCALS ? "final " : "") +
                           firstAssignment;
          variableDeclaration = tracker.text(lhs) + '=';
        }
      }
      else {
        final PsiDeclarationStatement declaration = (PsiDeclarationStatement)appendStatement;
        final PsiVariable variable = (PsiVariable)declaration.getDeclaredElements()[0];
        final PsiType variableType = variable.getType();
        if (variableType.equals(rootType)) {
          targetText = variable.getName();
          if (variable.hasModifierProperty(PsiModifier.FINAL)) {
            firstStatement = "final " + variableType.getCanonicalText() + ' ' + variable.getName() + '=' + root.getText() + ';';
          }
          else {
            firstStatement = variableType.getCanonicalText() + ' ' + variable.getName() + '=' + root.getText() + ';';
          }
          variableDeclaration = null;
          showRenameTemplate = false;
        }
        else {
          if (variable.hasModifierProperty(PsiModifier.FINAL)) {
            variableDeclaration = "final " + variableType.getCanonicalText() + ' ' + variable.getName() + '=';
          }
          else {
            variableDeclaration = variableType.getCanonicalText() + ' ' + variable.getName() + '=';
          }
          targetText = "x";
          showRenameTemplate = true;
          if (JavaCodeStyleSettings.getInstance(element.getContainingFile()).GENERATE_FINAL_LOCALS) {
            firstStatement = "final " + rootType.getCanonicalText() + " x=" + root.getText() + ';';
          }
          else {
            firstStatement = rootType.getCanonicalText() + " x=" + root.getText() + ';';
          }
        }
      }
    }
    final StringBuilder builder = new StringBuilder("{\n");
    if (firstStatement != null) {
      builder.append(firstStatement);
    }
    Collections.reverse(callTexts);
    for (int i = 0, size = callTexts.size(); i < size; i++) {
      final String callText = callTexts.get(i);
      if (i == size - 1 && variableDeclaration != null) {
        builder.append(variableDeclaration);
      }
      builder.append(targetText).append('.').append(callText).append(";\n");
    }
    builder.append('}');
    final PsiManager manager = element.getManager();
    final Project project = manager.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiElement appendStatementParent = appendStatement.getParent();
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
    PsiBlockStatement codeBlock = (PsiBlockStatement)factory.createStatementFromText(builder.toString(), appendStatement);
    if (appendStatementParent instanceof PsiLoopStatement || appendStatementParent instanceof PsiIfStatement) {
      PsiElement insertedCodeBlock = tracker.replaceAndRestoreComments(appendStatement, codeBlock);
      PsiBlockStatement reformattedCodeBlock = (PsiBlockStatement)codeStyleManager.reformat(insertedCodeBlock);
      if (showRenameTemplate) {
        PsiStatement[] statements = reformattedCodeBlock.getCodeBlock().getStatements();
        PsiVariable variable = (PsiVariable)((PsiDeclarationStatement)statements[0]).getDeclaredElements()[0];
        HighlightUtil.showRenameTemplate(appendStatementParent, variable);
      }
    }
    else {
      PsiStatement[] statements = codeBlock.getCodeBlock().getStatements();
      PsiVariable variable = null;
      for (int i = 0, length = statements.length; i < length; i++) {
        final PsiElement insertedStatement = appendStatementParent.addBefore(tracker.markUnchanged(statements[i]), appendStatement);
        if (i == 0 && showRenameTemplate) {
          variable = (PsiVariable)((PsiDeclarationStatement)insertedStatement).getDeclaredElements()[0];
        }
        codeStyleManager.reformat(insertedStatement);
      }
      tracker.deleteAndRestoreComments(appendStatement);
      if (variable != null) {
        HighlightUtil.showRenameTemplate(appendStatementParent, variable);
      }
    }
  }
}

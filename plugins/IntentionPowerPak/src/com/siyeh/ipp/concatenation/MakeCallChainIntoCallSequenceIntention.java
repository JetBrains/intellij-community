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
package com.siyeh.ipp.concatenation;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.util.IncorrectOperationException;
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
  public void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final List<String> callTexts = new ArrayList<>();
    PsiExpression root = (PsiExpression)element;
    while (root instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) root;
      final PsiExpressionList arguments = methodCallExpression.getArgumentList();
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      callTexts.add(methodExpression.getReferenceName() + arguments.getText());
      root = methodExpression.getQualifierExpression();
      if (root == null) {
        return;
      }
    }
    final PsiType rootType = root.getType();
    if (rootType == null) {
      return;
    }
    final String targetText;
    final PsiStatement appendStatement;
    @NonNls final String firstStatement;
    final String variableDeclaration;
    final boolean showRenameTemplate;
    final PsiElement parent = element.getParent();
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
        final PsiExpression lhs = assignment.getLExpression();
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
          targetText = lhs.getText();
          final PsiJavaToken token = assignment.getOperationSign();
          firstStatement = targetText + token.getText() + root.getText() + ';';
          showRenameTemplate = false;
        } else {
          targetText = "x";
          showRenameTemplate = true;
          final Project project = element.getProject();
          final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project);
          if (codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS) {
            firstStatement = "final " + rootType.getCanonicalText() + ' ' + targetText + '=' + root.getText() + ';';
          } else {
            firstStatement = rootType.getCanonicalText() + ' ' + targetText + '=' + root.getText() + ';';
          }
        }
        variableDeclaration = null;
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
        } else {
          if (variable.hasModifierProperty(PsiModifier.FINAL)) {
            variableDeclaration = "final " + variableType.getCanonicalText() + ' ' + variable.getName() + '=';
          }
          else {
            variableDeclaration = variableType.getCanonicalText() + ' ' + variable.getName() + '=';
          }
          targetText = "x";
          showRenameTemplate = true;
          final Project project = element.getProject();
          final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project);
          if (codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS) {
            firstStatement = "final " + rootType.getCanonicalText() + " x=" + root.getText() + ';';
          } else {
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
    final PsiCodeBlock codeBlock = factory.createCodeBlockFromText(builder.toString(), appendStatement);
    if (appendStatementParent instanceof PsiLoopStatement || appendStatementParent instanceof PsiIfStatement) {
      final PsiElement insertedCodeBlock = appendStatement.replace(codeBlock);
      final PsiCodeBlock reformattedCodeBlock = (PsiCodeBlock)codeStyleManager.reformat(insertedCodeBlock);
      if (showRenameTemplate) {
        final PsiStatement[] statements = reformattedCodeBlock.getStatements();
        final PsiVariable variable = (PsiVariable)((PsiDeclarationStatement) statements[0]).getDeclaredElements()[0];
        HighlightUtil.showRenameTemplate(appendStatementParent, variable);
      }
    }
    else {
      final PsiStatement[] statements = codeBlock.getStatements();
      PsiVariable variable = null;
      for (int i = 0, length = statements.length; i < length; i++) {
        final PsiElement insertedStatement = appendStatementParent.addBefore(statements[i], appendStatement);
        if (i == 0 && showRenameTemplate) {
          variable = (PsiVariable)((PsiDeclarationStatement) insertedStatement).getDeclaredElements()[0];
        }
        codeStyleManager.reformat(insertedStatement);
      }
      appendStatement.delete();
      if (variable != null) {
        HighlightUtil.showRenameTemplate(appendStatementParent, variable);
      }
    }
  }
}

/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.conditional;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceConditionalWithIfIntention extends Intention {

  private static final Logger LOG = Logger.getInstance("#" + ReplaceConditionalWithIfIntention.class.getName());

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ReplaceConditionalWithIfPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final PsiConditionalExpression expression = (PsiConditionalExpression)element;
    replaceConditionalWithIf(expression);
  }

  private static void replaceConditionalWithIf(PsiConditionalExpression expression) throws IncorrectOperationException {
    final PsiStatement statement = PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
    if (statement == null) {
      return;
    }
    final PsiVariable variable;
    if (statement instanceof PsiDeclarationStatement) {
      variable = PsiTreeUtil.getParentOfType(expression, PsiVariable.class);
    }
    else {
      variable = null;
    }
    PsiExpression thenExpression = expression.getThenExpression();
    PsiExpression elseExpression = expression.getElseExpression();
    final PsiExpression condition = expression.getCondition();
    final PsiExpression strippedCondition = ParenthesesUtils.stripParentheses(condition);
    final StringBuilder newStatement = new StringBuilder();
    newStatement.append("if(");
    if (strippedCondition != null) {
      newStatement.append(strippedCondition.getText());
    }
    newStatement.append(')');
    if (variable != null) {
      final String name = variable.getName();
      newStatement.append(name);
      newStatement.append('=');
      PsiExpression initializer = variable.getInitializer();
      if (initializer == null) {
        return;
      }
      if (initializer instanceof PsiArrayInitializerExpression) {
        final int conditionIdx = ArrayUtilRt.find(((PsiArrayInitializerExpression)initializer).getInitializers(), expression);
        if (conditionIdx >= 0) {
          initializer = (PsiExpression)initializer.replace(RefactoringUtil.convertInitializerToNormalExpression(initializer, variable.getType()));
          final PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)initializer).getArrayInitializer();
          LOG.assertTrue(arrayInitializer != null, initializer.getText());
          expression = (PsiConditionalExpression)arrayInitializer.getInitializers()[conditionIdx];
          thenExpression = expression.getThenExpression();
          elseExpression = expression.getElseExpression();
        }
      }
      appendElementTextWithoutParentheses(initializer, expression, thenExpression, newStatement);
      newStatement.append("; else ");
      newStatement.append(name);
      newStatement.append('=');
      appendElementTextWithoutParentheses(initializer, expression, elseExpression, newStatement);
      newStatement.append(';');
      initializer.delete();
      final PsiManager manager = statement.getManager();
      final Project project = manager.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = facade.getElementFactory();
      final PsiStatement ifStatement = factory.createStatementFromText(newStatement.toString(), statement);
      final PsiElement parent = statement.getParent();
      final PsiElement addedElement = parent.addAfter(ifStatement, statement);
      final CodeStyleManager styleManager = CodeStyleManager.getInstance(manager.getProject());
      styleManager.reformat(addedElement);
    }
    else {
      final boolean addBraces = PsiTreeUtil.getParentOfType(expression, PsiIfStatement.class, true, PsiStatement.class) != null;
      if (addBraces || thenExpression == null) {
        newStatement.append('{');
      }
      appendElementTextWithoutParentheses(statement, expression, thenExpression, newStatement);
      if (addBraces) {
        newStatement.append("} else {");
      }
      else {
        if (thenExpression == null) {
          newStatement.append('}');
        }
        newStatement.append(" else ");
        if (elseExpression == null) {
          newStatement.append('{');
        }
      }
      appendElementTextWithoutParentheses(statement, expression, elseExpression, newStatement);
      if (addBraces || elseExpression == null) {
        newStatement.append('}');
      }
      PsiReplacementUtil.replaceStatement(statement, newStatement.toString());
    }
  }

  private static void appendElementTextWithoutParentheses(@NotNull PsiElement element, @NotNull PsiElement elementToReplace,
                                                          @Nullable PsiExpression replacementExpression, @NotNull StringBuilder out) {
    final PsiElement expressionParent = elementToReplace.getParent();
    if (expressionParent instanceof PsiParenthesizedExpression) {
      final PsiElement grandParent = expressionParent.getParent();
      if (replacementExpression == null || !(grandParent instanceof PsiExpression) ||
          !ParenthesesUtils.areParenthesesNeeded(replacementExpression, (PsiExpression) grandParent, false)) {
        appendElementText(element, expressionParent, replacementExpression, out);
        return;
      }
    }
    appendElementText(element, elementToReplace, replacementExpression, out);
  }

  private static void appendElementText(@NotNull PsiElement element, @NotNull PsiElement elementToReplace,
                                        @Nullable PsiExpression replacementExpression, @NotNull StringBuilder out) {
    if (element.equals(elementToReplace)) {
      final String replacementText = (replacementExpression == null) ? "" : replacementExpression.getText();
      out.append(replacementText);
      return;
    }
    final PsiElement[] children = element.getChildren();
    if (children.length == 0) {
      out.append(element.getText());
      if (element instanceof PsiComment) {
        final PsiComment comment = (PsiComment)element;
        final IElementType tokenType = comment.getTokenType();
        if (tokenType == JavaTokenType.END_OF_LINE_COMMENT) {
          out.append('\n');
        }
      }
      return;
    }
    for (PsiElement child : children) {
      appendElementText(child, elementToReplace, replacementExpression, out);
    }
  }
}
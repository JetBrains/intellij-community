/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.increment;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExtractIncrementIntention extends MutablyNamedIntention {

  @Override
  public String getTextForElement(PsiElement element) {
    final PsiJavaToken sign;
    if (element instanceof PsiPostfixExpression) {
      sign = ((PsiPostfixExpression)element).getOperationSign();
    }
    else {
      sign = ((PsiPrefixExpression)element).getOperationSign();
    }
    final String operator = sign.getText();
    return IntentionPowerPackBundle.message(
      "extract.increment.intention.name", operator);
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ExtractIncrementPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element)
    throws IncorrectOperationException {
    final PsiExpression operand;
    if (element instanceof PsiPostfixExpression) {
      final PsiPostfixExpression postfixExpression =
        (PsiPostfixExpression)element;
      operand = postfixExpression.getOperand();
    }
    else {
      final PsiPrefixExpression prefixExpression =
        (PsiPrefixExpression)element;
      operand = prefixExpression.getOperand();
    }
    if (operand == null) {
      return;
    }
    final PsiStatement statement =
      PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    if (statement == null) {
      return;
    }
    final PsiElement parent = statement.getParent();
    if (parent == null) {
      return;
    }
    final Project project = element.getProject();
    final PsiElementFactory factory =
      JavaPsiFacade.getInstance(project).getElementFactory();
    final String newStatementText = element.getText() + ';';
    final String operandText = operand.getText();
    if (parent instanceof PsiIfStatement ||
        parent instanceof PsiLoopStatement) {
      // need to add braces because
      // in/decrement is inside braceless control statement body
      final StringBuilder text = new StringBuilder();
      text.append('{');
      final String elementText =
        getElementText(statement, element, operandText);
      if (element instanceof PsiPostfixExpression) {
        text.append(elementText);
        text.append(newStatementText);
      }
      else {
        text.append(newStatementText);
        text.append(elementText);
      }
      text.append('}');
      final PsiCodeBlock codeBlock =
        factory.createCodeBlockFromText(text.toString(), parent);
      statement.replace(codeBlock);
      return;
    }
    final PsiStatement newStatement =
      factory.createStatementFromText(newStatementText, element);
    if (statement instanceof PsiReturnStatement) {
      if (element instanceof PsiPostfixExpression) {
        // special handling of postfix expression in return statement
        final PsiReturnStatement returnStatement =
          (PsiReturnStatement)statement;
        final PsiExpression returnValue =
          returnStatement.getReturnValue();
        if (returnValue == null) {
          return;
        }
        final JavaCodeStyleManager javaCodeStyleManager =
          JavaCodeStyleManager.getInstance(project);
        final String variableName =
          javaCodeStyleManager.suggestUniqueVariableName(
            "result", returnValue, true);
        final PsiType type = returnValue.getType();
        if (type == null) {
          return;
        }
        final String newReturnValueText = getElementText(
          returnValue, element, operandText);
        final String declarationStatementText =
          type.getCanonicalText() + ' ' + variableName +
          '=' + newReturnValueText + ';';
        final PsiStatement declarationStatement =
          factory.createStatementFromText(declarationStatementText,
                                          returnStatement);
        parent.addBefore(declarationStatement, statement);
        parent.addBefore(newStatement, statement);
        final PsiStatement newReturnStatement =
          factory.createStatementFromText(
            "return " + variableName + ';',
            returnStatement);
        returnStatement.replace(newReturnStatement);
        return;
      }
      else {
        parent.addBefore(newStatement, statement);
      }
    }
    else if (statement instanceof PsiThrowStatement) {
      if (element instanceof PsiPostfixExpression) {
        // special handling of postfix expression in throw statement
        final PsiThrowStatement returnStatement =
          (PsiThrowStatement)statement;
        final PsiExpression exception =
          returnStatement.getException();
        if (exception == null) {
          return;
        }
        final JavaCodeStyleManager javaCodeStyleManager =
          JavaCodeStyleManager.getInstance(project);
        final String variableName =
          javaCodeStyleManager.suggestUniqueVariableName(
            "e", exception, true);
        final PsiType type = exception.getType();
        if (type == null) {
          return;
        }
        final String newReturnValueText = getElementText(
          exception, element, operandText);
        final String declarationStatementText =
          type.getCanonicalText() + ' ' + variableName +
          '=' + newReturnValueText + ';';
        final PsiStatement declarationStatement =
          factory.createStatementFromText(declarationStatementText,
                                          returnStatement);
        parent.addBefore(declarationStatement, statement);
        parent.addBefore(newStatement, statement);
        final PsiStatement newReturnStatement =
          factory.createStatementFromText(
            "throw " + variableName + ';',
            returnStatement);
        returnStatement.replace(newReturnStatement);
        return;
      }
      else {
        parent.addBefore(newStatement, statement);
      }
    }
    else if (!(statement instanceof PsiForStatement)) {
      if (element instanceof PsiPostfixExpression) {
        parent.addAfter(newStatement, statement);
      }
      else {
        parent.addBefore(newStatement, statement);
      }
    }
    else if (operand instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)operand;
      final PsiElement target = referenceExpression.resolve();
      if (target != null) {
        final SearchScope useScope = target.getUseScope();
        if (!new LocalSearchScope(statement).equals(useScope)) {
          if (element instanceof PsiPostfixExpression) {
            parent.addAfter(newStatement, statement);
          }
          else {
            parent.addBefore(newStatement, statement);
          }
        }
      }
    }
    if (statement instanceof PsiLoopStatement) {
      // in/decrement inside loop statement condition
      final PsiLoopStatement loopStatement = (PsiLoopStatement)statement;
      final PsiStatement body = loopStatement.getBody();
      if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement =
          (PsiBlockStatement)body;
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        if (element instanceof PsiPostfixExpression) {
          final PsiElement firstElement =
            codeBlock.getFirstBodyElement();
          codeBlock.addBefore(newStatement, firstElement);
        }
        else {
          codeBlock.add(newStatement);
        }
      }
      else {
        final StringBuilder blockText = new StringBuilder();
        blockText.append('{');
        if (element instanceof PsiPostfixExpression) {
          blockText.append(newStatementText);
          if (body != null) {
            blockText.append(body.getText());
          }
        }
        else {
          if (body != null) {
            blockText.append(body.getText());
          }
          blockText.append(newStatementText);
        }
        blockText.append('}');
        final PsiStatement blockStatement =
          factory.createStatementFromText(blockText.toString(),
                                          statement);
        if (body == null) {
          loopStatement.add(blockStatement);
        }
        else {
          body.replace(blockStatement);
        }
      }
    }
    replaceExpression(operandText, (PsiExpression)element);
  }

  private static String getElementText(@NotNull PsiElement element,
                                       @Nullable PsiElement elementToReplace,
                                       @Nullable String replacement) {
    final StringBuilder out = new StringBuilder();
    getElementText(element, elementToReplace, replacement, out);
    return out.toString();
  }

  private static void getElementText(
    @NotNull PsiElement element,
    @Nullable PsiElement elementToReplace,
    @Nullable String replacement,
    @NotNull StringBuilder out) {
    if (element.equals(elementToReplace)) {
      out.append(replacement);
      return;
    }
    final PsiElement[] children = element.getChildren();
    if (children.length == 0) {
      out.append(element.getText());
      return;
    }
    for (PsiElement child : children) {
      getElementText(child, elementToReplace, replacement, out);
    }
  }
}
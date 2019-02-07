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
package com.siyeh.ig.assignment;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IncrementDecrementUsedAsExpressionInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "ValueOfIncrementOrDecrementUsed";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "increment.decrement.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Object info = infos[0];
    if (info instanceof PsiPostfixExpression) {
      final PsiPostfixExpression postfixExpression =
        (PsiPostfixExpression)info;
      final IElementType tokenType =
        postfixExpression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.PLUSPLUS)) {
        return InspectionGadgetsBundle.message(
          "value.of.post.increment.problem.descriptor");
      }
      else {
        return InspectionGadgetsBundle.message(
          "value.of.post.decrement.problem.descriptor");
      }
    }
    else {
      final PsiPrefixExpression prefixExpression =
        (PsiPrefixExpression)info;
      final IElementType tokenType =
        prefixExpression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.PLUSPLUS)) {
        return InspectionGadgetsBundle.message(
          "value.of.pre.increment.problem.descriptor");
      }
      else {
        return InspectionGadgetsBundle.message(
          "value.of.pre.decrement.problem.descriptor");
      }
    }
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    if (PsiTreeUtil.getParentOfType(expression, PsiCodeBlock.class, true, PsiMember.class) == null) {
      return null;
    }
    return new IncrementDecrementUsedAsExpressionFix(expression.getText());
  }

  private static class IncrementDecrementUsedAsExpressionFix
    extends InspectionGadgetsFix {

    private final String elementText;

    IncrementDecrementUsedAsExpressionFix(String elementText) {
      this.elementText = elementText;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "increment.decrement.used.as.expression.quickfix",
        elementText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Extract to separate statement";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      extractPrefixPostfixExpressionToSeparateStatement(descriptor.getPsiElement());
    }
  }

  public static void extractPrefixPostfixExpressionToSeparateStatement(PsiElement element) {
    final PsiExpression operand;
    if (element instanceof PsiUnaryExpression) {
      final PsiUnaryExpression unaryExpression = (PsiUnaryExpression)element;
      operand = unaryExpression.getOperand();
    }
    else {
      assert false;
      return;
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
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final String newStatementText = element.getText() + ';';
    final String operandText = operand.getText();
    if (parent instanceof PsiIfStatement || parent instanceof PsiLoopStatement || parent instanceof PsiSwitchLabeledRuleStatement) {
      // need to add braces because
      // in/decrement is inside braceless control statement body
      final StringBuilder text = new StringBuilder();
      text.append('{');
      final String elementText = PsiReplacementUtil.getElementText(statement, element, operandText);
      if (element instanceof PsiPostfixExpression) {
        if (parent instanceof PsiSwitchLabeledRuleStatement) {
          text.append("break ");
        }
        text.append(elementText);
        text.append(newStatementText);
      }
      else {
        text.append(newStatementText);
        if (parent instanceof PsiSwitchLabeledRuleStatement) {
          text.append("break ");
        }
        text.append(elementText);
      }
      text.append('}');
      statement.replace(factory.createStatementFromText(text.toString(), parent));
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
        final PsiType type = returnValue.getType();
        if (type == null) {
          return;
        }
        final String variableName = new VariableNameGenerator(returnValue, VariableKind.LOCAL_VARIABLE).byType(type)
          .byExpression(returnValue).byName("result").generate(true);
        final String newReturnValueText = PsiReplacementUtil.getElementText(
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
        final String variableName = new VariableNameGenerator(exception, VariableKind.LOCAL_VARIABLE)
          .byName("e", "ex", "exc").generate(true);
        final PsiType type = exception.getType();
        if (type == null) {
          return;
        }
        final String newReturnValueText = PsiReplacementUtil.getElementText(
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
      final PsiLoopStatement loopStatement =
        (PsiLoopStatement)statement;
      final PsiStatement body = loopStatement.getBody();
      if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement =
          (PsiBlockStatement)body;
        final PsiCodeBlock codeBlock =
          blockStatement.getCodeBlock();
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
          factory.createStatementFromText(
            blockText.toString(), statement);
        if (body == null) {
          loopStatement.add(blockStatement);
        }
        else {
          body.replace(blockStatement);
        }
      }
    }
    PsiReplacementUtil.replaceExpression((PsiExpression)element, operandText);
  }

  public static boolean isSuitableForReplacement(@NotNull PsiUnaryExpression expression) {
    if (ExpressionUtils.isVoidContext(expression)) {
      return false;
    }
    final IElementType tokenType = expression.getOperationTokenType();
    if (!tokenType.equals(JavaTokenType.PLUSPLUS) && !tokenType.equals(JavaTokenType.MINUSMINUS)) {
      return false;
    }
    return PsiTreeUtil.getParentOfType(expression, PsiStatement.class) != null;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IncrementDecrementUsedAsExpressionVisitor();
  }

  private static class IncrementDecrementUsedAsExpressionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      super.visitUnaryExpression(expression);

      if (isSuitableForReplacement(expression)) {
        registerError(expression, expression);
      }
    }
  }
}
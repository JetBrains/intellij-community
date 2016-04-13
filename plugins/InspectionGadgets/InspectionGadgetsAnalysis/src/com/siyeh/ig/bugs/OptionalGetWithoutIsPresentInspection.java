/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class OptionalGetWithoutIsPresentInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("optional.get.without.is.present.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    assert aClass != null;
    return InspectionGadgetsBundle.message("optional.get.without.is.present.problem.descriptor", aClass.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OptionalGetWithoutIsPresentVisitor();
  }

  private static class OptionalGetWithoutIsPresentVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (!"get".equals(name) && !"getAsDouble".equals(name) && !"getAsInt".equals(name) && !"getAsLong".equals(name)) {
        return;
      }
      final PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
      if (qualifier == null) {
        return;
      }
      final PsiType type = qualifier.getType();
      if (!TypeUtils.isOptional(type)) {
        return;
      }
      if (qualifier instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
        if (isSurroundedByIsPresentGuard(referenceExpression, expression)) {
          return;
        }
      }
      registerMethodCallError(expression, type);
    }
  }

  private static boolean isSurroundedByIsPresentGuard(PsiReferenceExpression optionalReference, PsiElement context) {
    PsiStatement sibling = PsiTreeUtil.getParentOfType(context, PsiStatement.class);
    sibling = PsiTreeUtil.getPrevSiblingOfType(sibling, PsiStatement.class);
    final IsPresentChecker checker = new IsPresentChecker(optionalReference);
    while (sibling != null) {
      if (sibling instanceof PsiIfStatement) {
        final PsiIfStatement ifStatement = (PsiIfStatement)sibling;
        final PsiExpression condition = ifStatement.getCondition();
        if (condition != null) {
          final PsiElement target = optionalReference.resolve();
          if (!(target instanceof PsiVariable)) {
            return true;
          }
          final PsiVariable variable = (PsiVariable)target;
          final PsiStatement thenBranch = ifStatement.getThenBranch();
          if (!ControlFlowUtils.statementMayCompleteNormally(thenBranch) || VariableAccessUtils.variableIsAssigned(variable, thenBranch)) {
            checker.negate = true;
            if (checker.checkExpression(condition)) {
              return true;
            }
          }
          final PsiStatement elseBranch = ifStatement.getElseBranch();
          if (!ControlFlowUtils.statementMayCompleteNormally(elseBranch) || VariableAccessUtils.variableIsAssigned(variable, elseBranch)) {
            checker.negate = false;
            if (checker.checkExpression(condition)) {
              return true;
            }
          }
        }
      }
      else if (sibling instanceof PsiWhileStatement) {
        final PsiWhileStatement whileStatement = (PsiWhileStatement)sibling;
        final PsiExpression condition = whileStatement.getCondition();
        checker.negate = true;
        if (checker.checkExpression(condition)) {
          return true;
        }
      }
      else if (sibling instanceof PsiAssertStatement) {
        final PsiAssertStatement assertStatement = (PsiAssertStatement)sibling;
        final PsiExpression condition = assertStatement.getAssertCondition();
        checker.negate = false;
        if (checker.checkExpression(condition)) {
          return true;
        }
      }
      else if (sibling instanceof PsiExpressionStatement) {
        final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)sibling;
        final PsiExpression expression = expressionStatement.getExpression();
        if (expression instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
          if (MethodCallUtils.isCallToMethod(methodCallExpression, "org.junit.Assert", PsiType.VOID, "assertTrue", null) ||
              MethodCallUtils.isCallToMethod(methodCallExpression, "junit.framework.Assert", PsiType.VOID, "assertTrue", null) ||
              MethodCallUtils.isCallToMethod(methodCallExpression, "org.testng.Assert", PsiType.VOID, "assertTrue", null) ||
              MethodCallUtils.isCallToMethod(methodCallExpression, "org.testng.AssertJUnit", PsiType.VOID, "assertTrue", null)) {
            checker.negate = false;
            final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            for (PsiExpression argument : arguments) {
              if (checker.checkExpression(argument)) {
                return true;
              }
            }
          }
        }
      }
      sibling = PsiTreeUtil.getPrevSiblingOfType(sibling, PsiStatement.class);
    }
    checker.negate = false;
    PsiElement parent = PsiTreeUtil.getParentOfType(context, PsiIfStatement.class, PsiWhileStatement.class, PsiConditionalExpression.class,
                                                    PsiPolyadicExpression.class);
    while (parent != null) {
      if (parent instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
        if (JavaTokenType.OROR.equals(polyadicExpression.getOperationTokenType())) {
          checker.negate = true;
        }
      }
      parent.accept(checker);
      if (checker.hasIsPresentCall()) {
        return true;
      }
      parent = PsiTreeUtil.getParentOfType(parent, PsiIfStatement.class, PsiWhileStatement.class, PsiConditionalExpression.class,
                                           PsiPolyadicExpression.class);
    }
    return checker.hasIsPresentCall();
  }

  private static class IsPresentChecker extends JavaElementVisitor {

    private final PsiReferenceExpression referenceExpression;
    private boolean negate = false;
    private boolean hasIsPresentCall = false;


    IsPresentChecker(PsiReferenceExpression referenceExpression) {
      this.referenceExpression = referenceExpression;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitExpression(expression);
    }

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (tokenType != JavaTokenType.ANDAND && tokenType != JavaTokenType.OROR) {
        return;
      }
      for (PsiExpression operand : expression.getOperands()) {
        if (PsiTreeUtil.isAncestor(operand, referenceExpression, false)) {
          return;
        }
        checkExpression(operand);
        if (hasIsPresentCall) {
          return;
        }
      }
    }

    @Override
    public void visitWhileStatement(PsiWhileStatement statement) {
      checkExpression(statement.getCondition());
    }

    @Override
    public void visitIfStatement(PsiIfStatement ifStatement) {
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      negate = elseBranch != null && PsiTreeUtil.isAncestor(elseBranch, referenceExpression, true);
      final PsiStatement statement = negate ? elseBranch : ifStatement.getThenBranch();
      if (statement instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)statement;
        if (VariableAccessUtils.variableIsAssignedBeforeReference(referenceExpression, blockStatement)) {
          return;
        }
      }
      checkExpression(ifStatement.getCondition());
    }

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      final PsiExpression elseExpression = expression.getElseExpression();
      negate = elseExpression != null && PsiTreeUtil.isAncestor(elseExpression, referenceExpression, true);
      checkExpression(expression.getCondition());
    }

    private boolean checkExpression(PsiExpression expression) {
      expression = PsiUtil.deparenthesizeExpression(expression);
      if (expression instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
        final IElementType tokenType = prefixExpression.getOperationTokenType();
        if (tokenType != JavaTokenType.EXCL) {
          return false;
        }
        negate = !negate;
        return checkExpression(prefixExpression.getOperand());
      }
      else if (expression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression binaryExpression = (PsiPolyadicExpression)expression;
        visitPolyadicExpression(binaryExpression);
      }
      else if (expression instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final String name = methodExpression.getReferenceName();
        if (!"isPresent".equals(name)) {
          return false;
        }
        final PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
        if (!(qualifier instanceof PsiReferenceExpression)) {
          return false;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
        hasIsPresentCall = !negate && EquivalenceChecker.expressionsAreEquivalent(referenceExpression, this.referenceExpression);
      }
      else if (expression instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
        final PsiExpression definition = VariableSearchUtils.findDefinition(referenceExpression, null);
        final PsiExpression optionalDefinition = VariableSearchUtils.findDefinition(this.referenceExpression, null);
        if (definition == null || optionalDefinition == null || optionalDefinition.getTextOffset() > definition.getTextOffset()) {
          return false;
        }
        return checkExpression(definition);
      }
      return hasIsPresentCall;
    }

    public boolean hasIsPresentCall() {
      return hasIsPresentCall;
    }
  }
}

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
package com.siyeh.ig.logging;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LogStatementGuardedByLogConditionInspectionBase extends BaseInspection {
  final List<String> logMethodNameList = new ArrayList();
  final List<String> logConditionMethodNameList = new ArrayList();
  @SuppressWarnings({"PublicField"})
  public String loggerClassName = "java.util.logging.Logger";
  @NonNls
  @SuppressWarnings({"PublicField"})
  public String loggerMethodAndconditionMethodNames =
    "fine,isLoggable(java.util.logging.Level.FINE)," +
    "finer,isLoggable(java.util.logging.Level.FINER)," +
    "finest,isLoggable(java.util.logging.Level.FINEST)";
  @SuppressWarnings("PublicField")
  public boolean flagAllUnguarded = false;

  public LogStatementGuardedByLogConditionInspectionBase() {
    parseString(loggerMethodAndconditionMethodNames, logMethodNameList, logConditionMethodNameList);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("log.statement.guarded.by.log.condition.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("log.statement.guarded.by.log.condition.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new LogStatementGuardedByLogConditionFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LogStatementGuardedByLogConditionVisitor();
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(loggerMethodAndconditionMethodNames, logMethodNameList, logConditionMethodNameList);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    loggerMethodAndconditionMethodNames = formatString(logMethodNameList, logConditionMethodNameList);
    super.writeSettings(element);
  }

  private class LogStatementGuardedByLogConditionFix extends InspectionGadgetsFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("log.statement.guarded.by.log.condition.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element.getParent().getParent();
      final PsiStatement statement = PsiTreeUtil.getParentOfType(methodCallExpression, PsiStatement.class);
      if (statement == null) {
        return;
      }
      final List<PsiStatement> logStatements = new ArrayList();
      logStatements.add(statement);
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (referenceName == null) {
        return;
      }
      PsiStatement previousStatement = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
      while (previousStatement != null && isSameLogMethodCall(previousStatement, referenceName)) {
        logStatements.add(0, previousStatement);
        previousStatement = PsiTreeUtil.getPrevSiblingOfType(previousStatement, PsiStatement.class);
      }
      PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
      while (nextStatement != null && isSameLogMethodCall(nextStatement, referenceName)) {
        logStatements.add(nextStatement);
        nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement.class);
      }
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final int index = logMethodNameList.indexOf(referenceName);
      final String conditionMethodText = logConditionMethodNameList.get(index);
      @NonNls final String ifStatementText = "if (" + qualifier.getText() + '.' + conditionMethodText + ") {}";
      final PsiIfStatement ifStatement = (PsiIfStatement)factory.createStatementFromText(ifStatementText, statement);
      final PsiBlockStatement blockStatement = (PsiBlockStatement)ifStatement.getThenBranch();
      if (blockStatement == null) {
        return;
      }
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      for (PsiStatement logStatement : logStatements) {
        codeBlock.add(logStatement);
      }
      final PsiStatement firstStatement = logStatements.get(0);
      final PsiElement parent = firstStatement.getParent();
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      if (parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getElseBranch() != null) {
        final PsiBlockStatement newBlockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", statement);
        newBlockStatement.getCodeBlock().add(ifStatement);
        final PsiElement result = firstStatement.replace(newBlockStatement);
        codeStyleManager.shortenClassReferences(result);
        return;
      }
      final PsiElement result = parent.addBefore(ifStatement, firstStatement);
      codeStyleManager.shortenClassReferences(result);
      for (PsiStatement logStatement : logStatements) {
        logStatement.delete();
      }
    }

    private boolean isSameLogMethodCall(PsiStatement statement, @NotNull String methodName) {
      if (statement == null) {
        return false;
      }
      if (!(statement instanceof PsiExpressionStatement)) {
        return false;
      }
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (!methodName.equals(referenceName)) {
        return false;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      return qualifier != null && TypeUtils.expressionHasTypeOrSubtype(qualifier, loggerClassName);
    }
  }

  private class LogStatementGuardedByLogConditionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (!logMethodNameList.contains(referenceName)) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, loggerClassName)) {
        return;
      }
      if (isSurroundedByLogGuard(expression, referenceName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      if (!flagAllUnguarded) {
        boolean constant = true;
        for (PsiExpression argument : arguments) {
          if (!PsiUtil.isConstantExpression(argument)) {
            constant = false;
            break;
          }
        }
        if (constant) {
          return;
        }
      }
      registerMethodCallError(expression);
    }

    private boolean isSurroundedByLogGuard(PsiElement element, String logMethodName) {
      while (true) {
        final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
        if (ifStatement == null) {
          return false;
        }
        final PsiExpression condition = ifStatement.getCondition();
        if (isLogGuardCheck(condition, logMethodName)) {
          return true;
        }
        element = ifStatement;
      }
    }

    private boolean isLogGuardCheck(@Nullable PsiExpression expression, String logMethodName) {
      expression = ParenthesesUtils.stripParentheses(expression);
      if (expression instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, loggerClassName)) {
          return false;
        }
        final String referenceName = methodExpression.getReferenceName();
        if (referenceName == null) {
          return false;
        }
        final int index = logMethodNameList.indexOf(logMethodName);
        final String conditionName = logConditionMethodNameList.get(index);
        return conditionName.startsWith(referenceName);
      }
      else if (expression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        final PsiExpression[] operands = polyadicExpression.getOperands();
        for (PsiExpression operand : operands) {
          if (isLogGuardCheck(operand, logMethodName)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}

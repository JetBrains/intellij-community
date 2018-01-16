/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public class StringConcatenationArgumentToLogCallInspectionBase extends BaseInspection {

  @NonNls
  private static final Set<String> logNames = new THashSet<>();
  static {
    logNames.add("trace");
    logNames.add("debug");
    logNames.add("info");
    logNames.add("warn");
    logNames.add("error");
    logNames.add("fatal");
    logNames.add("log");
  }

  @SuppressWarnings("PublicField") public int warnLevel = 0;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.problem.descriptor");
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (warnLevel != 0) {
      node.addContent(new Element("option").setAttribute("name", "warnLevel").setAttribute("value", String.valueOf(warnLevel)));
    }
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    if (!StringConcatenationArgumentToLogCallFix.isAvailable((PsiExpression)infos[0])) {
      return null;
    }
    return new StringConcatenationArgumentToLogCallFix();
  }

  private static class StringConcatenationArgumentToLogCallFix extends InspectionGadgetsFix {

    public StringConcatenationArgumentToLogCallFix() {}

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement grandParent = element.getParent().getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      @NonNls final StringBuilder newMethodCall = new StringBuilder(methodCallExpression.getMethodExpression().getText());
      newMethodCall.append('(');
      PsiExpression argument = arguments[0];
      int usedArguments;
      if (!(argument instanceof PsiPolyadicExpression)) {
        if (!TypeUtils.expressionHasTypeOrSubtype(argument, "org.slf4j.Marker") || arguments.length < 2) {
          return;
        }
        newMethodCall.append(argument.getText()).append(',');
        argument = arguments[1];
        usedArguments = 2;
        if (!(argument instanceof PsiPolyadicExpression)) {
          return;
        }
      }
      else {
        usedArguments = 1;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)argument;
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final String methodName = method.getName();
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final PsiMethod[] methods = containingClass.findMethodsByName(methodName, false);
      boolean varArgs = false;
      for (PsiMethod otherMethod : methods) {
        if (otherMethod.isVarArgs()) {
          varArgs = true;
          break;
        }
      }
      final List<PsiExpression> newArguments = new ArrayList();
      final PsiExpression[] operands = polyadicExpression.getOperands();
      boolean addPlus = false;
      boolean inStringLiteral = false;
      for (PsiExpression operand : operands) {
        if (ExpressionUtils.isEvaluatedAtCompileTime(operand)) {
          if (ExpressionUtils.hasStringType(operand) && operand instanceof PsiLiteralExpression) {
            final String text = operand.getText();
            final int count = StringUtil.getOccurrenceCount(text, "{}");
            for (int i = 0; i < count && usedArguments + i < arguments.length; i++) {
              newArguments.add(ParenthesesUtils.stripParentheses((PsiExpression)arguments[i + usedArguments].copy()));
            }
            usedArguments += count;
            if (!inStringLiteral) {
              if (addPlus) {
                newMethodCall.append('+');
              }
              newMethodCall.append('"');
              inStringLiteral = true;
            }
            newMethodCall.append(text.substring(1, text.length() - 1));
          }
          else {
            if (inStringLiteral) {
              newMethodCall.append('"');
              inStringLiteral = false;
            }
            if (addPlus) {
              newMethodCall.append('+');
            }
            newMethodCall.append(operand.getText());
          }
        }
        else {
          newArguments.add(ParenthesesUtils.stripParentheses((PsiExpression)operand.copy()));
          if (!inStringLiteral) {
            if (addPlus) {
              newMethodCall.append('+');
            }
            newMethodCall.append('"');
            inStringLiteral = true;
          }
          newMethodCall.append("{}");
        }
        addPlus = true;
      }
      while (usedArguments < arguments.length) {
        newArguments.add(arguments[usedArguments++]);
      }
      if (inStringLiteral) {
        newMethodCall.append('"');
      }
      if (!varArgs && newArguments.size() > 2) {
        newMethodCall.append(", new Object[]{");
        boolean comma = false;
        for (PsiExpression newArgument : newArguments) {
          if (comma) {
            newMethodCall.append(',');
          }
          else {
            comma = true;
          }
          if (newArgument != null) {
            newMethodCall.append(newArgument.getText());
          }
        }
        newMethodCall.append('}');
      }
      else {
        for (PsiExpression newArgument : newArguments) {
          newMethodCall.append(',');
          if (newArgument != null) {
            newMethodCall.append(newArgument.getText());
          }
        }
      }
      newMethodCall.append(')');
      PsiReplacementUtil.replaceExpression(methodCallExpression, newMethodCall.toString());
    }

    public static boolean isAvailable(PsiExpression expression) {
      if (!(expression instanceof PsiPolyadicExpression)) {
        return false;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (!ExpressionUtils.isEvaluatedAtCompileTime(operand)) {
          return true;
        }
      }
      return false;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationArgumentToLogCallVisitor();
  }

  private class StringConcatenationArgumentToLogCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (!logNames.contains(referenceName)) {
        return;
      }
      switch (warnLevel) {
        case 4: if ("debug".equals(referenceName)) return;
        case 3: if ("info".equals(referenceName)) return;
        case 2: if ("warn".equals(referenceName)) return;
        case 1: if ("error".equals(referenceName) || "fatal".equals(referenceName)) return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (!InheritanceUtil.isInheritor(containingClass, "org.slf4j.Logger") &&
          !InheritanceUtil.isInheritor(containingClass, "org.apache.logging.log4j.Logger")) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      PsiExpression argument = arguments[0];
      if (!ExpressionUtils.hasStringType(argument)) {
        if (arguments.length < 2) {
          return;
        }
        argument = arguments[1];
        if (!ExpressionUtils.hasStringType(argument)) {
          return;
        }
      }
      if (!containsNonConstantConcatenation(argument)) {
        return;
      }
      registerMethodCallError(expression, argument);
    }

    private boolean containsNonConstantConcatenation(@Nullable PsiExpression expression) {
      if (expression instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
        return containsNonConstantConcatenation(parenthesizedExpression.getExpression());
      }
      else if (expression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        if (!ExpressionUtils.hasStringType(polyadicExpression)) {
          return false;
        }
        if (!JavaTokenType.PLUS.equals(polyadicExpression.getOperationTokenType())) {
          return false;
        }
        final PsiExpression[] operands = polyadicExpression.getOperands();
        for (PsiExpression operand : operands) {
          if (!ExpressionUtils.isEvaluatedAtCompileTime(operand)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}

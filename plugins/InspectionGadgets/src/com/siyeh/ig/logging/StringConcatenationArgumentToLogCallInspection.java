// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.logging;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ui.FormBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public class StringConcatenationArgumentToLogCallInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NonNls
  private static final Set<String> logNames = new HashSet<>();
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

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final ComboBox<String> comboBox = new ComboBox<>(new String[]{
      InspectionGadgetsBundle.message("all.levels.option"),
      InspectionGadgetsBundle.message("warn.level.and.lower.option"),
      InspectionGadgetsBundle.message("info.level.and.lower.option"),
      InspectionGadgetsBundle.message("debug.level.and.lower.option"),
      InspectionGadgetsBundle.message("trace.level.option")
    });
    comboBox.setSelectedIndex(warnLevel);
    comboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        warnLevel = comboBox.getSelectedIndex();
      }
    });
    return new FormBuilder()
      .addLabeledComponent(InspectionGadgetsBundle.message("warn.on.label"), comboBox)
      .addVerticalGap(-1)
      .getPanel();
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

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationArgumentToLogCallVisitor();
  }

  private static class StringConcatenationArgumentToLogCallFix extends InspectionGadgetsFix {

    StringConcatenationArgumentToLogCallFix() {}

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
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
      final List<PsiExpression> newArguments = new ArrayList<>();
      final PsiExpression[] operands = polyadicExpression.getOperands();
      boolean addPlus = false;
      boolean inStringLiteral = false;
      for (PsiExpression operand : operands) {
        if (ExpressionUtils.isEvaluatedAtCompileTime(operand)) {
          final String text = operand.getText();
          if (ExpressionUtils.hasStringType(operand) && operand instanceof PsiLiteralExpression) {
            final int count = StringUtil.getOccurrenceCount(text, "{}");
            for (int i = 0; i < count && usedArguments + i < arguments.length; i++) {
              newArguments.add(PsiUtil.skipParenthesizedExprDown((PsiExpression)arguments[i + usedArguments].copy()));
            }
            usedArguments += count;
            if (!inStringLiteral) {
              if (addPlus) {
                newMethodCall.append('+');
              }
              newMethodCall.append('"');
              inStringLiteral = true;
            }
            newMethodCall.append(text, 1, text.length() - 1);
          }
          else if (operand instanceof PsiLiteralExpression && PsiType.CHAR.equals(operand.getType()) && inStringLiteral) {
            final Object value = ((PsiLiteralExpression)operand).getValue();
            if (value instanceof Character) {
              newMethodCall.append(StringUtil.escapeStringCharacters(value.toString()));
            }
          }
          else {
            if (inStringLiteral) {
              newMethodCall.append('"');
              inStringLiteral = false;
            }
            if (addPlus) {
              newMethodCall.append('+');
            }
            newMethodCall.append(text);
          }
        }
        else {
          newArguments.add(PsiUtil.skipParenthesizedExprDown((PsiExpression)operand.copy()));
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

  private class StringConcatenationArgumentToLogCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
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
          !InheritanceUtil.isInheritor(containingClass, "org.apache.logging.log4j.Logger") &&
          !InheritanceUtil.isInheritor(containingClass, "org.apache.logging.log4j.LogBuilder")) {
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

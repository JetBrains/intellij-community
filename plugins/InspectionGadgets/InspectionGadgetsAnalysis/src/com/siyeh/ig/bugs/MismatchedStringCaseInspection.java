// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThreeState;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;

public class MismatchedStringCaseInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher STRING_COMPARISON_METHODS = CallMatcher.exactInstanceCall(
    CommonClassNames.JAVA_LANG_STRING, "equals", "startsWith", "endsWith", "contains", "indexOf", "lastIndexOf");

  private static final CallMatcher CASE_PRESERVING_METHODS = CallMatcher.exactInstanceCall(
    CommonClassNames.JAVA_LANG_STRING, "trim", "repeat", "substring", "strip");

  private static final CallMatcher TO_LOWER_CASE = CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_STRING, "toLowerCase");
  private static final CallMatcher TO_UPPER_CASE = CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_STRING, "toUpperCase");
  private static final int ANALYSIS_COMPLEXITY = 16;

  private static class StringCase {
    static final StringCase UNSURE = new StringCase(ThreeState.UNSURE, ThreeState.UNSURE);
    final @NotNull ThreeState myHasLower, myHasUpper;

    StringCase(@NotNull ThreeState hasLower, @NotNull ThreeState hasUpper) {
      myHasLower = hasLower;
      myHasUpper = hasUpper;
    }

    StringCase either(@NotNull StringCase other) {
      ThreeState hasLower = myHasLower == ThreeState.NO && other.myHasLower == ThreeState.NO ? ThreeState.NO :
                            myHasLower == ThreeState.YES && other.myHasLower == ThreeState.YES ? ThreeState.YES : ThreeState.UNSURE;
      ThreeState hasUpper = myHasUpper == ThreeState.NO && other.myHasUpper == ThreeState.NO ? ThreeState.NO :
                            myHasUpper == ThreeState.YES && other.myHasUpper == ThreeState.YES ? ThreeState.YES : ThreeState.UNSURE;
      return new StringCase(hasLower, hasUpper);
    }

    StringCase concat(@NotNull StringCase other) {
      ThreeState hasLower = myHasLower == ThreeState.YES || other.myHasLower == ThreeState.YES ? ThreeState.YES :
                            myHasLower == ThreeState.NO && other.myHasLower == ThreeState.NO ? ThreeState.NO :
                            ThreeState.UNSURE;
      ThreeState hasUpper = myHasUpper == ThreeState.YES || other.myHasUpper == ThreeState.YES ? ThreeState.YES :
                            myHasUpper == ThreeState.NO && other.myHasUpper == ThreeState.NO ? ThreeState.NO :
                            ThreeState.UNSURE;
      return new StringCase(hasLower, hasUpper);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      StringCase aCase = (StringCase)o;
      return myHasLower == aCase.myHasLower &&
             myHasUpper == aCase.myHasUpper;
    }
  }

  static StringCase fromExpression(@Nullable PsiExpression expression, int energy) {
    if (expression == null || energy <= 0) return StringCase.UNSURE;
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    String str = tryCast(ExpressionUtils.computeConstantExpression(expression), String.class);
    if (str != null) {
      return fromConstant(str);
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      if (CASE_PRESERVING_METHODS.test(call)) {
        return fromExpression(call.getMethodExpression().getQualifierExpression(), energy - 1);
      }
      if (TO_LOWER_CASE.test(call)) {
        return new StringCase(ThreeState.UNSURE, ThreeState.NO);
      }
      if (TO_UPPER_CASE.test(call)) {
        return new StringCase(ThreeState.NO, ThreeState.UNSURE);
      }
    }
    if (expression instanceof PsiConditionalExpression) {
      PsiExpression thenBranch = ((PsiConditionalExpression)expression).getThenExpression();
      StringCase thenCase = fromExpression(thenBranch, energy / 2);
      if (thenCase.equals(StringCase.UNSURE)) return thenCase;
      PsiExpression elseBranch = ((PsiConditionalExpression)expression).getElseExpression();
      return fromExpression(elseBranch, energy / 2).either(thenCase);
    }
    if (expression instanceof PsiPolyadicExpression &&
        ((PsiPolyadicExpression)expression).getOperationTokenType().equals(JavaTokenType.PLUS)) {
      PsiExpression[] operands = ((PsiPolyadicExpression)expression).getOperands();
      StringCase result = new StringCase(ThreeState.NO, ThreeState.NO);
      for (PsiExpression operand : operands) {
        StringCase operandCase = fromExpression(operand, energy / operands.length);
        result = result.concat(operandCase);
        if (result.myHasLower == ThreeState.YES && result.myHasUpper == ThreeState.YES) {
          break;
        }
      }
      return result;
    }
    if (expression instanceof PsiReferenceExpression) {
      return fromExpression(resolveDefinition(expression), energy - 4);
    }
    return StringCase.UNSURE;
  }

  @Nullable
  private static PsiExpression resolveDefinition(@NotNull PsiExpression expression) {
    PsiReferenceExpression referenceExpression = tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiReferenceExpression.class);
    if (referenceExpression == null) return null;
    PsiVariable variable = tryCast(referenceExpression.resolve(), PsiVariable.class);
    if (variable == null) return null;
    PsiCodeBlock block = tryCast(PsiUtil.getVariableCodeBlock(variable, null), PsiCodeBlock.class);
    if (block == null) return null;
    PsiElement[] defs = DefUseUtil.getDefs(block, variable, expression);
    if (defs.length != 1) return null;
    PsiElement def = defs[0];
    if(def instanceof PsiLocalVariable) {
      return ((PsiLocalVariable)def).getInitializer();
    }
    if(def instanceof PsiReferenceExpression) {
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(def.getParent());
      if(assignment != null && assignment.getLExpression() == def) {
        return assignment.getRExpression();
      }
    }
    if(def instanceof PsiExpression) {
      return (PsiExpression)def;
    }
    return null;
  }

  private static StringCase fromConstant(String str) {
    ThreeState hasLower = ThreeState.NO, hasUpper = ThreeState.NO;
    for (int i = 0; i < str.length(); ) {
      int codepoint = str.codePointAt(i);

      if (Character.isLowerCase(codepoint)) {
        hasLower = ThreeState.YES;
        if (hasUpper == ThreeState.YES) break;
      }
      else if (Character.isUpperCase(codepoint)) {
        hasUpper = ThreeState.YES;
        if (hasLower == ThreeState.YES) break;
      }

      i += Character.charCount(codepoint);
    }
    return new StringCase(hasLower, hasUpper);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        if (!STRING_COMPARISON_METHODS.test(call)) return;
        PsiExpression arg = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        StringCase argCase = fromExpression(arg, ANALYSIS_COMPLEXITY);
        if (argCase.myHasUpper != ThreeState.YES && argCase.myHasLower != ThreeState.YES) return;
        StringCase qualifierCase = fromExpression(qualifier, ANALYSIS_COMPLEXITY);
        String problematicCase;
        String oppositeCase;
        if (qualifierCase.myHasLower == ThreeState.NO && argCase.myHasLower == ThreeState.YES) {
          problematicCase = "a lowercase";
          oppositeCase = "uppercase";
        } else if (qualifierCase.myHasUpper == ThreeState.NO && argCase.myHasUpper == ThreeState.YES) {
          problematicCase = "an uppercase";
          oppositeCase = "lowercase";
        } else {
          return;
        }
        PsiElement anchor = Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement());
        String methodName = anchor.getText();
        String returnValue;
        switch (methodName) {
          case "indexOf":
          case "lastIndexOf":
            returnValue = "-1";
            break;
          default:
            returnValue = "false";
        }
        String message = InspectionGadgetsBundle.message("inspection.case.mismatch.message",
                                                         methodName, returnValue, problematicCase, oppositeCase);
        holder.registerProblem(anchor, message);
      }
    };
  }
}

/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.numeric;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;

public class ConstantMathCallInspection extends ExpressionInspection {

  @SuppressWarnings("StaticCollection")
  @NonNls private static final Set<String> constantMathCall = new HashSet<String>(20);

  static {
    constantMathCall.add("abs");
    constantMathCall.add("acos");
    constantMathCall.add("asin");
    constantMathCall.add("atan");
    constantMathCall.add("atan2");
    constantMathCall.add("cbrt");
    constantMathCall.add("ceil");
    constantMathCall.add("cos");
    constantMathCall.add("cosh");
    constantMathCall.add("cbrt");
    constantMathCall.add("exp");
    constantMathCall.add("expm1");
    constantMathCall.add("floor");
    constantMathCall.add("log");
    constantMathCall.add("log10");
    constantMathCall.add("rint");
    constantMathCall.add("round");
    constantMathCall.add("sin");
    constantMathCall.add("sinh");
    constantMathCall.add("sqrt");
    constantMathCall.add("tan");
    constantMathCall.add("tanh");
    constantMathCall.add("toDegrees");
    constantMathCall.add("toRadians");
  }

  private final MakeStrictFix fix = new MakeStrictFix();

  public String getGroupDisplayName() {
    return GroupNames.NUMERIC_GROUP_NAME;
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class MakeStrictFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiIdentifier nameIdentifier =
        (PsiIdentifier)descriptor.getPsiElement();
      final PsiReferenceExpression reference =
        (PsiReferenceExpression)nameIdentifier.getParent();
      assert reference != null;
      final PsiMethodCallExpression call =
        (PsiMethodCallExpression)reference.getParent();

      assert call != null;
      final PsiExpressionList argList = call.getArgumentList();

      assert argList != null;
      final PsiExpression[] args = argList.getExpressions();

      final String methodName = reference.getReferenceName();
      final PsiExpression arg = args[0];
      final Double argValue =
        (Double)ConstantExpressionUtil
          .computeCastTo(arg, PsiType.DOUBLE);
      final String newExpression = createValueString(methodName,
                                                     argValue);
      replaceExpressionAndShorten(call, newExpression);
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ConstantMathCallVisitor();
  }

  private static class ConstantMathCallVisitor extends BaseInspectionVisitor {
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      if (methodExpression == null) {
        return;
      }
      final String methodName = methodExpression.getReferenceName();
      if (!constantMathCall.contains(methodName)) {
        return;
      }
      final PsiExpressionList argList = expression.getArgumentList();
      if (argList == null) {
        return;
      }
      final PsiExpression[] args = argList.getExpressions();
      if (args.length == 0) {
        return;
      }
      final PsiExpression arg = args[0];
      final Object argValue = ConstantExpressionUtil.computeCastTo(arg,
                                                                   PsiType.DOUBLE);
      if (argValue == null || !(argValue instanceof Double)) {
        return;
      }
      final double doubleValue = (Double)argValue;
      final String valueString = createValueString(methodName,
                                                   doubleValue);
      if (valueString == null) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass referencedClass = method.getContainingClass();
      if (referencedClass == null) {
        return;
      }
      final String className = referencedClass.getQualifiedName();
      if (!"java.lang.Math".equals(className)
          && !"java.lang.StrictMath".equals(className)) {
        return;
      }

      registerMethodCallError(expression);
    }
  }

  @SuppressWarnings({"NestedMethodCall", "FloatingPointEquality"})
  @Nullable
  @NonNls
  private static String createValueString(@NonNls String name, double value) {
    if ("abs".equals(name)) {
      return Double.toString(Math.abs(value));
    }
    else if ("floor".equals(name)) {
      return Double.toString(Math.floor(value));
    }
    else if ("ceil".equals(name)) {
      return Double.toString(Math.ceil(value));
    }
    else if ("toDegrees".equals(name)) {
      return Double.toString(Math.toDegrees(value));
    }
    else if ("toRadians".equals(name)) {
      return Double.toString(Math.toRadians(value));
    }
    else if ("sqrt".equals(name)) {
      return Double.toString(Math.sqrt(value));
    }
    else if ("cbrt".equals(name)) {
      return Double.toString(Math.cbrt(value));
    }
    else if ("round".equals(name)) {
      return Long.toString(Math.round(value));
    }
    else if ("rint".equals(name)) {
      return Double.toString(Math.rint(value));
    }
    else if ("log".equals(name)) {
      if (value == 1.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("log10".equals(name)) {
      if (value == 1.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("exp".equals(name)) {
      if (value == 0.0) {
        return "1.0";
      }
      else if (value == 1.0) {
        return "Math.E";
      }
      else {
        return null;
      }
    }
    else if ("expm1".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("cos".equals(name) || "cosh".equals(name)) {
      if (value == 0.0) {
        return "1.0";
      }
      else {
        return null;
      }
    }
    else if ("acos".equals(name)) {
      if (value == 1.0) {
        return "0.0";
      }
      else if (value == 0.0) {
        return "(Math.PI/2.0)";
      }
      else {
        return null;
      }
    }
    else if ("acosh".equals(name)) {
      if (value == 1.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("sin".equals(name) || "sinh".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("asin".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else if (value == 1.0) {
        return "(Math.PI/2.0)";
      }
      else {
        return null;
      }
    }
    else if ("asinh".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("tan".equals(name) || "tanh".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("atan".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else if (value == 1.0) {
        return "(Math.PI/4.0)";
      }
      else {
        return null;
      }
    }
    else if ("atan2".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("atanh".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    return null;
  }
}

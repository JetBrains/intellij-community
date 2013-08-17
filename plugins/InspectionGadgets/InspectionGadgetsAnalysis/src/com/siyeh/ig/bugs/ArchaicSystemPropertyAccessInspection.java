/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ArchaicSystemPropertyAccessInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "UseOfArchaicSystemPropertyAccessors";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "archaic.system.property.accessors.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiMethodCallExpression call =
      (PsiMethodCallExpression)infos[0];
    if (isIntegerGetInteger(call)) {
      return InspectionGadgetsBundle.message(
        "archaic.system.property.accessors.problem.descriptor.Integer");
    }
    else if (isLongGetLong(call)) {
      return InspectionGadgetsBundle.message(
        "archaic.system.property.accessors.problem.descriptor.Long");
    }
    else {
      return InspectionGadgetsBundle.message(
        "archaic.system.property.accessors.problem.descriptor.Boolean");
    }
  }

  @Override
  @NotNull
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    return new InspectionGadgetsFix[]{new ReplaceWithParseMethodFix(),
      new ReplaceWithStandardPropertyAccessFix()};
  }

  private static class ReplaceWithParseMethodFix extends InspectionGadgetsFix {
     @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "archaic.system.property.accessors.replace.parse.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiIdentifier location =
        (PsiIdentifier)descriptor.getPsiElement();
      final PsiElement parent = location.getParent();
      assert parent != null;
      final PsiMethodCallExpression call =
        (PsiMethodCallExpression)parent.getParent();
      assert call != null;
      final PsiExpressionList argList = call.getArgumentList();
      final PsiExpression[] args = argList.getExpressions();
      final String argText = args[0].getText();
      @NonNls final String parseMethodCall;
      if (isIntegerGetInteger(call)) {
        parseMethodCall = "Integer.valueOf(" + argText + ')';
      }
      else if (isLongGetLong(call)) {
        parseMethodCall = "Long.valueOf(" + argText + ')';
      }
      else {
        parseMethodCall = "Boolean.valueOf(" + argText + ')';
      }
      replaceExpression(call, parseMethodCall);
    }
  }

  private static class ReplaceWithStandardPropertyAccessFix
    extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "archaic.system.property.accessors.replace.standard.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiIdentifier location =
        (PsiIdentifier)descriptor.getPsiElement();
      final PsiElement parent = location.getParent();
      assert parent != null;
      final PsiMethodCallExpression call =
        (PsiMethodCallExpression)parent.getParent();
      assert call != null;
      final PsiExpressionList argList = call.getArgumentList();
      final PsiExpression[] args = argList.getExpressions();
      final String argText = args[0].getText();
      @NonNls final String parseMethodCall;
      if (isIntegerGetInteger(call)) {
        parseMethodCall = "Integer.parseInt(System.getProperty("
                          + argText + "))";
      }
      else if (isLongGetLong(call)) {
        parseMethodCall = "Long.parseLong(System.getProperty("
                          + argText + "))";
      }
      else {
        if (!PsiUtil.isLanguageLevel5OrHigher(call)) {
          parseMethodCall = "Boolean.valueOf(System.getProperty("
                            + argText + ")).booleanValue()";
        }
        else {
          parseMethodCall = "Boolean.parseBoolean(System.getProperty("
                            + argText + "))";
        }
      }
      replaceExpression(call, parseMethodCall);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArchaicSystemPropertyAccessVisitor();
  }

  private static class ArchaicSystemPropertyAccessVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (isIntegerGetInteger(expression) ||
          isLongGetLong(expression) ||
          isBooleanGetBoolean(expression)) {
        registerMethodCallError(expression, expression);
      }
    }
  }

  static boolean isIntegerGetInteger(PsiMethodCallExpression expression) {
    return isCallTo(expression, CommonClassNames.JAVA_LANG_INTEGER,
                    "getInteger");
  }

  static boolean isLongGetLong(PsiMethodCallExpression expression) {
    return isCallTo(expression, CommonClassNames.JAVA_LANG_LONG, "getLong");
  }

  static boolean isBooleanGetBoolean(PsiMethodCallExpression expression) {
    return isCallTo(expression, CommonClassNames.JAVA_LANG_BOOLEAN,
                    "getBoolean");
  }

  private static boolean isCallTo(PsiMethodCallExpression expression,
                                  String className, @NonNls String methodName) {
    final PsiReferenceExpression methodExpression =
      expression.getMethodExpression();
    @NonNls final String expressionMethodName =
      methodExpression.getReferenceName();
    if (!methodName.equals(expressionMethodName)) {
      return false;
    }
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return false;
    }
    final String expressionClassName = aClass.getQualifiedName();
    if (expressionClassName == null) {
      return false;
    }
    return className.equals(expressionClassName);
  }
}
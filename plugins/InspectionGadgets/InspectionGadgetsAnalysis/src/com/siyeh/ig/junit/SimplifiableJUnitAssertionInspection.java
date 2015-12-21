/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.junit;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SimplifiableJUnitAssertionInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("simplifiable.junit.assertion.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("simplifiable.junit.assertion.problem.descriptor", infos[0]);
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new SimplifyJUnitAssertFix();
  }

  private static class SimplifyJUnitAssertFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("simplify.junit.assertion.simplify.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = methodNameIdentifier.getParent();
      if (parent == null) {
        return;
      }
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)parent.getParent();
      if (isAssertThatCouldBeAssertNull(callExpression)) {
        replaceAssertWithAssertNull(callExpression);
      }
      else if (isAssertThatCouldBeAssertSame(callExpression)) {
        replaceAssertWithAssertSame(callExpression);
      }
      else if (isAssertTrueThatCouldBeAssertEquals(callExpression)) {
        replaceAssertTrueWithAssertEquals(callExpression);
      }
      else if (isAssertEqualsThatCouldBeAssertLiteral(callExpression)) {
        replaceAssertEqualsWithAssertLiteral(callExpression);
      }
      else if (isAssertThatCouldBeFail(callExpression)) {
        replaceAssertWithFail(callExpression);
      }
    }

    private static void addStaticImportOrQualifier(String methodName, PsiMethodCallExpression originalMethodCall, StringBuilder out) {
      final PsiReferenceExpression methodExpression = originalMethodCall.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        final PsiMethod method = originalMethodCall.resolveMethod();
        if (method == null) {
          return;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && "org.junit.Assert".equals(containingClass.getQualifiedName()) &&
            !ImportUtils.addStaticImport("org.junit.Assert", methodName, originalMethodCall)) {
          // add qualifier if old call was to JUnit4 method and adding static import failed
          out.append("org.junit.Assert.");
        }
      }
      else {
        // apparently not statically imported, keep old qualifier in new assert call
        out.append(qualifier.getText()).append('.');
      }
    }

    private static void replaceAssertWithFail(PsiMethodCallExpression callExpression) {
      final PsiMethod method = callExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiExpressionList argumentList = callExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression message;
      if (arguments.length == 2) {
        message = arguments[0];
      }
      else {
        message = null;
      }
      @NonNls final StringBuilder newExpression = new StringBuilder();
      addStaticImportOrQualifier("fail", callExpression, newExpression);
      newExpression.append("fail(");
      if (message != null) {
        newExpression.append(message.getText());
      }
      newExpression.append(')');
      PsiReplacementUtil.replaceExpressionAndShorten(callExpression, newExpression.toString());
    }

    private static void replaceAssertTrueWithAssertEquals(PsiMethodCallExpression callExpression) {
      final PsiMethod method = callExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiType stringType = TypeUtils.getStringType(callExpression);
      final PsiType paramType1 = parameters[0].getType();
      final PsiExpressionList argumentList = callExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final int testPosition;
      final PsiExpression message;
      if (paramType1.equals(stringType) && parameters.length >= 2) {
        testPosition = 1;
        message = arguments[0];
      }
      else {
        testPosition = 0;
        message = null;
      }
      final PsiExpression testArgument = arguments[testPosition];
      PsiExpression lhs = null;
      PsiExpression rhs = null;
      if (testArgument instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)testArgument;
        lhs = binaryExpression.getLOperand();
        rhs = binaryExpression.getROperand();
      }
      else if (testArgument instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression call = (PsiMethodCallExpression)testArgument;
        final PsiReferenceExpression equalityMethodExpression = call.getMethodExpression();
        final PsiExpressionList equalityArgumentList = call.getArgumentList();
        final PsiExpression[] equalityArgs = equalityArgumentList.getExpressions();
        rhs = equalityArgs[0];
        lhs = equalityMethodExpression.getQualifierExpression();
      }
      if (!(lhs instanceof PsiLiteralExpression) && rhs instanceof PsiLiteralExpression) {
        final PsiExpression temp = lhs;
        lhs = rhs;
        rhs = temp;
      }
      if (lhs == null || rhs == null) {
        return;
      }
      @NonNls final StringBuilder newExpression = new StringBuilder();
      addStaticImportOrQualifier("assertEquals", callExpression, newExpression);
      newExpression.append("assertEquals(");
      if (message != null) {
        newExpression.append(message.getText()).append(',');
      }
      final PsiType lhsType = lhs.getType();
      final PsiType rhsType = rhs.getType();
      if (lhsType != null && rhsType != null && PsiUtil.isLanguageLevel5OrHigher(lhs)) {
        if (isPrimitiveAndBoxedInteger(lhsType, rhsType)) {
          final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(rhsType);
          assert unboxedType != null;
          newExpression.append(lhs.getText()).append(",(").append(unboxedType.getCanonicalText()).append(')').append(rhs.getText());
        }
        else if (isPrimitiveAndBoxedInteger(rhsType, lhsType)) {
          final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(lhsType);
          assert unboxedType != null;
          newExpression.append('(').append(unboxedType.getCanonicalText()).append(')').append(lhs.getText()).append(',').append(rhs.getText());
        }
        else {
          newExpression.append(lhs.getText()).append(',').append(rhs.getText());
        }
      }
      else {
        newExpression.append(lhs.getText()).append(',').append(rhs.getText());
      }
      if (TypeUtils.hasFloatingPointType(lhs) || TypeUtils.hasFloatingPointType(rhs) ||
          isPrimitiveAndBoxedFloat(lhsType, rhsType) || isPrimitiveAndBoxedFloat(rhsType, lhsType)) {
        newExpression.append(",0.0");
      }
      newExpression.append(')');
      PsiReplacementUtil.replaceExpressionAndShorten(callExpression, newExpression.toString());
    }

    private static boolean isPrimitiveAndBoxedInteger(PsiType lhsType, PsiType rhsType) {
      return lhsType instanceof PsiPrimitiveType && rhsType instanceof PsiClassType && PsiType.LONG.isAssignableFrom(rhsType);
    }

    private static boolean isPrimitiveAndBoxedFloat(PsiType lhsType, PsiType rhsType) {
      return lhsType instanceof PsiPrimitiveType && rhsType instanceof PsiClassType &&
             (PsiType.DOUBLE.equals(rhsType) && PsiType.FLOAT.equals(rhsType));
    }

    private static void replaceAssertWithAssertNull(PsiMethodCallExpression callExpression) {
      final PsiMethod method = callExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiType stringType = TypeUtils.getStringType(callExpression);
      final PsiType paramType1 = parameters[0].getType();
      final PsiExpressionList argumentList = callExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final int testPosition;
      final PsiExpression message;
      if (paramType1.equals(stringType) && parameters.length >= 2) {
        testPosition = 1;
        message = arguments[0];
      }
      else {
        testPosition = 0;
        message = null;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)arguments[testPosition];
      final PsiExpression lhs = binaryExpression.getLOperand();
      PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!(lhs instanceof PsiLiteralExpression) && rhs instanceof PsiLiteralExpression) {
        rhs = lhs;
      }
      @NonNls final StringBuilder newExpression = new StringBuilder();
      final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      @NonNls final String memberName;
      if ("assertFalse".equals(methodName) ^ tokenType.equals(JavaTokenType.NE)) {
        memberName = "assertNotNull";
      }
      else {
        memberName = "assertNull";
      }
      addStaticImportOrQualifier(memberName, callExpression, newExpression);
      newExpression.append(memberName).append('(');
      if (message != null) {
        newExpression.append(message.getText()).append(',');
      }
      newExpression.append(rhs.getText()).append(')');
      PsiReplacementUtil.replaceExpressionAndShorten(callExpression, newExpression.toString());
    }

    private static void replaceAssertWithAssertSame(PsiMethodCallExpression callExpression) {
      final PsiMethod method = callExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiType stringType = TypeUtils.getStringType(callExpression);
      final PsiType paramType1 = parameters[0].getType();
      final PsiExpressionList argumentList = callExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final int testPosition;
      final PsiExpression message;
      if (paramType1.equals(stringType) && parameters.length >= 2) {
        testPosition = 1;
        message = arguments[0];
      }
      else {
        testPosition = 0;
        message = null;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)arguments[testPosition];
      PsiExpression lhs = binaryExpression.getLOperand();
      PsiExpression rhs = binaryExpression.getROperand();
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!(lhs instanceof PsiLiteralExpression) && rhs instanceof PsiLiteralExpression) {
        final PsiExpression temp = lhs;
        lhs = rhs;
        rhs = temp;
      }
      if (rhs == null) {
        return;
      }
      @NonNls final StringBuilder newExpression = new StringBuilder();
      final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      @NonNls final String memberName;
      if ("assertFalse".equals(methodName) ^ tokenType.equals(JavaTokenType.NE)) {
        memberName = "assertNotSame";
      }
      else {
        memberName = "assertSame";
      }
      addStaticImportOrQualifier(memberName, callExpression, newExpression);
      newExpression.append(memberName).append('(');
      if (message != null) {
        newExpression.append(message.getText()).append(',');
      }
      newExpression.append(lhs.getText()).append(',').append(rhs.getText()).append(')');
      PsiReplacementUtil.replaceExpressionAndShorten(callExpression, newExpression.toString());
    }

    private static void replaceAssertEqualsWithAssertLiteral(PsiMethodCallExpression callExpression) {
      final PsiMethod method = callExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiType stringType = TypeUtils.getStringType(callExpression);
      final PsiType paramType1 = parameters[0].getType();
      final PsiExpressionList argumentList = callExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final int firstTestPosition;
      final int secondTestPosition;
      final PsiExpression message;
      if (paramType1.equals(stringType) && parameters.length >= 3) {
        firstTestPosition = 1;
        secondTestPosition = 2;
        message = arguments[0];
      }
      else {
        firstTestPosition = 0;
        secondTestPosition = 1;
        message = null;
      }
      final PsiExpression firstTestArgument = arguments[firstTestPosition];
      final PsiExpression secondTestArgument = arguments[secondTestPosition];
      final String literalValue;
      final String compareValue;
      if (isSimpleLiteral(firstTestArgument, secondTestArgument)) {
        literalValue = firstTestArgument.getText();
        compareValue = secondTestArgument.getText();
      }
      else {
        literalValue = secondTestArgument.getText();
        compareValue = firstTestArgument.getText();
      }
      final String uppercaseLiteralValue = Character.toUpperCase(literalValue.charAt(0)) + literalValue.substring(1);
      @NonNls final StringBuilder newExpression = new StringBuilder();
      @NonNls final String methodName = "assert" + uppercaseLiteralValue;
      addStaticImportOrQualifier(methodName, callExpression, newExpression);
      newExpression.append(methodName).append('(');
      if (message != null) {
        newExpression.append(message.getText()).append(',');
      }
      newExpression.append(compareValue).append(')');
      PsiReplacementUtil.replaceExpressionAndShorten(callExpression, newExpression.toString());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SimplifiableJUnitAssertionVisitor();
  }

  private static class SimplifiableJUnitAssertionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (isAssertThatCouldBeAssertNull(expression)) {
        if (hasEqEqExpressionArgument(expression)) {
          registerMethodCallError(expression, "assertNull()");
        }
        else {
          registerMethodCallError(expression, "assertNotNull()");
        }
      }
      else if (isAssertThatCouldBeAssertSame(expression)) {
        if (hasEqEqExpressionArgument(expression)) {
          registerMethodCallError(expression, "assertSame()");
        }
        else {
          registerMethodCallError(expression, "assertNotSame()");
        }
      }
      else if (isAssertTrueThatCouldBeAssertEquals(expression)) {
        registerMethodCallError(expression, "assertEquals()");
      }
      else if (isAssertEqualsThatCouldBeAssertLiteral(expression)) {
        registerMethodCallError(expression, getReplacementMethodName(expression));
      }
      else if (isAssertThatCouldBeFail(expression)) {
        registerMethodCallError(expression, "fail()");
      }
    }

    @NonNls
    private static String getReplacementMethodName(PsiMethodCallExpression expression) {
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression firstArgument = arguments[0];
      if (firstArgument instanceof PsiLiteralExpression) {
        final PsiLiteralExpression literalExpression = (PsiLiteralExpression)firstArgument;
        final Object value = literalExpression.getValue();
        if (value == Boolean.TRUE) {
          return "assertTrue()";
        }
        else if (value == Boolean.FALSE) {
          return "assertFalse()";
        }
        else if (value == null) {
          return "assertNull()";
        }
      }
      final PsiExpression secondArgument = arguments[1];
      if (secondArgument instanceof PsiLiteralExpression) {
        final PsiLiteralExpression literalExpression = (PsiLiteralExpression)secondArgument;
        final Object value = literalExpression.getValue();
        if (value == Boolean.TRUE) {
          return "assertTrue()";
        }
        else if (value == Boolean.FALSE) {
          return "assertFalse()";
        }
        else if (value == null) {
          return "assertNull()";
        }
      }
      return "";
    }

    private static boolean hasEqEqExpressionArgument(PsiMethodCallExpression expression) {
      final PsiExpressionList list = expression.getArgumentList();
      final PsiExpression[] arguments = list.getExpressions();
      final PsiExpression argument = arguments[0];
      if (!(argument instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)argument;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      return JavaTokenType.EQEQ.equals(tokenType);
    }
  }

  static boolean isAssertTrueThatCouldBeAssertEquals(
    PsiMethodCallExpression expression) {
    if (!isAssertTrue(expression)) {
      return false;
    }
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiMethod method = (PsiMethod)methodExpression.resolve();
    if (method == null) {
      return false;
    }
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() < 1) {
      return false;
    }
    final PsiType stringType = TypeUtils.getStringType(expression);
    final PsiParameter[] parameters = parameterList.getParameters();
    final PsiType paramType1 = parameters[0].getType();
    final int testPosition;
    if (paramType1.equals(stringType) && parameters.length > 1) {
      testPosition = 1;
    }
    else {
      testPosition = 0;
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    final PsiExpression testArgument = arguments[testPosition];
    return testArgument != null && isEqualityComparison(testArgument);
  }

  static boolean isAssertThatCouldBeAssertSame(PsiMethodCallExpression expression) {
    if (!isAssertTrue(expression) && !isAssertFalse(expression)) {
      return false;
    }
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiMethod method = (PsiMethod)methodExpression.resolve();
    if (method == null) {
      return false;
    }
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() < 1) {
      return false;
    }
    final PsiType stringType = TypeUtils.getStringType(expression);
    final PsiParameter[] parameters = parameterList.getParameters();
    final PsiType paramType1 = parameters[0].getType();
    final int testPosition;
    if (paramType1.equals(stringType) && parameters.length > 1) {
      testPosition = 1;
    }
    else {
      testPosition = 0;
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    final PsiExpression testArgument = arguments[testPosition];
    return testArgument != null && isIdentityComparison(testArgument);
  }

  static boolean isAssertThatCouldBeAssertNull(PsiMethodCallExpression expression) {
    if (!isAssertTrue(expression) && !isAssertFalse(expression)) {
      return false;
    }
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiMethod method = (PsiMethod)methodExpression.resolve();
    if (method == null) {
      return false;
    }
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() < 1) {
      return false;
    }
    final PsiType stringType = TypeUtils.getStringType(expression);
    final PsiParameter[] parameters = parameterList.getParameters();
    final PsiType paramType1 = parameters[0].getType();
    final int testPosition;
    if (paramType1.equals(stringType) && parameters.length > 1) {
      testPosition = 1;
    }
    else {
      testPosition = 0;
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    final PsiExpression testArgument = arguments[testPosition];
    return testArgument != null && isNullComparison(testArgument);
  }


  static boolean isAssertThatCouldBeFail(PsiMethodCallExpression expression) {
    final boolean checkTrue;
    if (isAssertFalse(expression)) {
      checkTrue = true;
    }
    else if (isAssertTrue(expression)) {
      checkTrue = false;
    }
    else {
      return false;
    }
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiMethod method = (PsiMethod)methodExpression.resolve();
    if (method == null) {
      return false;
    }
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() < 1) {
      return false;
    }
    final PsiType stringType = TypeUtils.getStringType(expression);
    final PsiParameter[] parameters = parameterList.getParameters();
    final PsiType paramType1 = parameters[0].getType();
    final int testPosition;
    if (paramType1.equals(stringType) && parameters.length > 1) {
      testPosition = 1;
    }
    else {
      testPosition = 0;
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    final PsiExpression testArgument = arguments[testPosition];
    if (testArgument == null) {
      return false;
    }
    final String testArgumentText = testArgument.getText();
    if (checkTrue) {
      return PsiKeyword.TRUE.equals(testArgumentText);
    }
    else {
      return PsiKeyword.FALSE.equals(testArgumentText);
    }
  }

  static boolean isAssertEqualsThatCouldBeAssertLiteral(PsiMethodCallExpression expression) {
    if (!isAssertEquals(expression)) {
      return false;
    }
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiMethod method = (PsiMethod)methodExpression.resolve();
    if (method == null) {
      return false;
    }
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() < 2) {
      return false;
    }
    final PsiType stringType = TypeUtils.getStringType(expression);
    final PsiParameter[] parameters = parameterList.getParameters();
    final PsiType paramType1 = parameters[0].getType();
    final int firstTestPosition;
    final int secondTestPosition;
    if (paramType1.equals(stringType) && parameters.length > 2) {
      firstTestPosition = 1;
      secondTestPosition = 2;
    }
    else {
      firstTestPosition = 0;
      secondTestPosition = 1;
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    final PsiExpression firstTestArgument = arguments[firstTestPosition];
    final PsiExpression secondTestArgument = arguments[secondTestPosition];
    if (firstTestArgument == null || secondTestArgument == null) {
      return false;
    }
    return isSimpleLiteral(firstTestArgument, secondTestArgument) ||
           isSimpleLiteral(secondTestArgument, firstTestArgument);
  }

  static boolean isSimpleLiteral(PsiExpression expression1, PsiExpression expression2) {
    if (!(expression1 instanceof PsiLiteralExpression)) {
      return false;
    }
    final String text = expression1.getText();
    if (PsiKeyword.NULL.equals(text)) {
      return true;
    }
    if (!PsiKeyword.TRUE.equals(text) && !PsiKeyword.FALSE.equals(text)) {
      return false;
    }
    final PsiType type = expression2.getType();
    return PsiType.BOOLEAN.equals(type);
  }

  private static boolean isEqualityComparison(PsiExpression expression) {
    if (expression instanceof PsiBinaryExpression) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.EQEQ)) {
        return false;
      }
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return false;
      }
      final PsiType type = lhs.getType();
      return type != null && ClassUtils.isPrimitive(type);
    }
    else if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      if (!MethodCallUtils.isEqualsCall(call)) {
        return false;
      }
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      return methodExpression.getQualifierExpression() != null;
    }
    return false;
  }

  private static boolean isIdentityComparison(PsiExpression expression) {
    if (!(expression instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
    if (!ComparisonUtils.isEqualityComparison(binaryExpression)) {
      return false;
    }
    final PsiExpression rhs = binaryExpression.getROperand();
    if (rhs == null) {
      return false;
    }
    final PsiExpression lhs = binaryExpression.getLOperand();
    final PsiType lhsType = lhs.getType();
    if (lhsType instanceof PsiPrimitiveType) {
      return false;
    }
    final PsiType rhsType = rhs.getType();
    return !(rhsType instanceof PsiPrimitiveType);
  }

  private static boolean isNullComparison(PsiExpression expression) {
    if (!(expression instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
    if (!ComparisonUtils.isEqualityComparison(binaryExpression)) {
      return false;
    }
    final PsiExpression rhs = binaryExpression.getROperand();
    if (rhs == null) {
      return false;
    }
    final PsiExpression lhs = binaryExpression.getLOperand();
    return PsiKeyword.NULL.equals(lhs.getText()) || PsiKeyword.NULL.equals(rhs.getText());
  }

  private static boolean isAssertTrue(@NotNull PsiMethodCallExpression expression) {
    return isAssertMethodCall(expression, "assertTrue");
  }

  private static boolean isAssertFalse(@NotNull PsiMethodCallExpression expression) {
    return isAssertMethodCall(expression, "assertFalse");
  }

  private static boolean isAssertEquals(@NotNull PsiMethodCallExpression expression) {
    return isAssertMethodCall(expression, "assertEquals");
  }

  private static boolean isAssertMethodCall(@NotNull PsiMethodCallExpression expression,
    @NonNls @NotNull String assertMethodName) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!assertMethodName.equals(methodName)) {
      return false;
    }
    final PsiMethod method = (PsiMethod)methodExpression.resolve();
    if (method == null) {
      return false;
    }
    final PsiClass targetClass = method.getContainingClass();
    if (targetClass == null) {
      return false;
    }
    final String qualifiedName = targetClass.getQualifiedName();
    return "junit.framework.Assert".equals(qualifiedName) || "org.junit.Assert".equals(qualifiedName);
  }
}

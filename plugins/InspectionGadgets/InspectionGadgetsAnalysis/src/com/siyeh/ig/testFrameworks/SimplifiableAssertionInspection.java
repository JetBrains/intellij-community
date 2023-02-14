// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.testFrameworks;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SimplifiableAssertionInspection extends BaseInspection implements CleanupLocalInspectionTool {
  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("simplifiable.junit.assertion.problem.descriptor", infos[0]);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new SimplifyAssertFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SimplifiableJUnitAssertionVisitor();
  }

  static boolean isAssertThatCouldBeFail(PsiExpression position, boolean checkTrue) {
    return (checkTrue ? PsiKeyword.TRUE : PsiKeyword.FALSE).equals(position.getText());
  }

  boolean isAssertEqualsThatCouldBeAssertLiteral(AssertHint assertHint) {
    final PsiExpression firstTestArgument = assertHint.getFirstArgument();
    final PsiExpression secondTestArgument = assertHint.getSecondArgument();
    return isSimpleLiteral(firstTestArgument, secondTestArgument) ||
           isSimpleLiteral(secondTestArgument, firstTestArgument);
  }

  static boolean isSimpleLiteral(PsiExpression expression1, PsiExpression expression2) {
    if (!(expression1 instanceof PsiLiteralExpression) || expression2 == null) {
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
    return PsiTypes.booleanType().equals(type);
  }

  static boolean isEqualityComparison(PsiExpression expression) {
    if (expression instanceof PsiBinaryExpression binaryExpression) {
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
      return type != null && TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(type);
    }
    return EqualityCheck.from(expression) != null;
  }

  static final CallMatcher ARRAYS_EQUALS = CallMatcher.staticCall("java.util.Arrays", "equals").parameterCount(2);
  static boolean isArrayEqualityComparison(PsiExpression expression) {
    return expression instanceof PsiMethodCallExpression && ARRAYS_EQUALS.test((PsiMethodCallExpression)expression);
  }

  static boolean isIdentityComparison(PsiExpression expression) {
    if (!(expression instanceof PsiBinaryExpression binaryExpression)) {
      return false;
    }
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

  private class SimplifyAssertFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("simplify.junit.assertion.simplify.quickfix");
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = methodNameIdentifier.getParent();
      if (parent == null) {
        return;
      }
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)parent.getParent();
      final AssertHint assertHint = AssertHint.createAssertEqualsHint(callExpression);
      if (assertHint != null && isAssertEqualsThatCouldBeAssertLiteral(assertHint)) {
        replaceAssertEqualsWithAssertLiteral(assertHint);
      }
      else {
        final AssertHint assertTrueFalseHint = AssertHint.createAssertTrueFalseHint(callExpression);
        if (assertTrueFalseHint == null) {
          return;
        }
        final boolean assertTrue = assertTrueFalseHint.isAssertTrue();
        final PsiExpression argument = assertTrueFalseHint.getFirstArgument();
        if (ComparisonUtils.isNullComparison(argument)) {
          replaceAssertWithAssertNull(assertTrueFalseHint);
        }
        else if (isIdentityComparison(argument)) {
          replaceWithAssertSame(assertTrueFalseHint);
        }
        else if (assertTrue && isEqualityComparison(argument)) {
          replaceWithAssertEquals(assertTrueFalseHint, "assertEquals");
        }
        else if (isAssertThatCouldBeFail(argument, !assertTrue)) {
          replaceWithFail(assertTrueFalseHint);
        }
        else if (isEqualityComparison(argument)) {
          replaceWithAssertEquals(assertTrueFalseHint, "assertNotEquals");
        }
        else if (assertTrue && isArrayEqualityComparison(argument)) {
          replaceWithAssertEquals(assertTrueFalseHint, "assertArrayEquals");
        }
        else if (BoolUtils.isNegation(argument)) {
          replaceWithNegatedBooleanAssertion(assertTrueFalseHint);
        }
      }
    }

    private void addStaticImportOrQualifier(String methodName, AssertHint assertHint, StringBuilder out) {
      final PsiMethodCallExpression originalMethodCall = (PsiMethodCallExpression)assertHint.getOriginalExpression();
      final PsiReferenceExpression methodExpression = originalMethodCall.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        final PsiMethod method = assertHint.getMethod();
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
          return;
        }
        final String className = containingClass.getQualifiedName();
        if (className == null) {
          return;
        }
        if (!ImportUtils.addStaticImport(className, methodName, originalMethodCall)) {
          // add qualifier if old call was to JUnit4 method and adding static import failed
          out.append(className).append(".");
        }
      }
      else {
        // apparently not statically imported, keep old qualifier in new assert call
        out.append(qualifier.getText()).append('.');
      }
    }

    private void replaceWithFail(AssertHint assertHint) {
      @NonNls final StringBuilder newExpression = new StringBuilder();
      addStaticImportOrQualifier("fail", assertHint, newExpression);
      newExpression.append("fail(");
      final PsiExpression message = assertHint.getMessage();
      if (message != null) {
        newExpression.append(message.getText());
      }
      newExpression.append(')');
      PsiReplacementUtil.replaceExpressionAndShorten(assertHint.getOriginalExpression(), newExpression.toString());
    }

    /**
     * <code>assertTrue</code> -> <code>assertEquals</code>
     * <p/
     * <code>assertFalse</code> -> <code>assertNotEquals</code> (do not replace for junit 5 Assertions
     * as there is no primitive overloads for <code>assertNotEquals</code> and boxing would be enforced if replaced)
     */
    private void replaceWithAssertEquals(AssertHint assertHint, final @NonNls String methodName) {
      final PsiExpression firstArgument = assertHint.getFirstArgument();
      PsiExpression lhs = null;
      PsiExpression rhs = null;
      if (firstArgument instanceof PsiBinaryExpression binaryExpression) {
        lhs = binaryExpression.getLOperand();
        rhs = binaryExpression.getROperand();
      }
      else {
        final EqualityCheck check = EqualityCheck.from(firstArgument);
        if (check != null) {
          lhs = check.getLeft();
          rhs = check.getRight();
        }
        else if (firstArgument instanceof PsiMethodCallExpression && ARRAYS_EQUALS.test((PsiMethodCallExpression)firstArgument)) {
          final PsiExpression[] args = ((PsiMethodCallExpression)firstArgument).getArgumentList().getExpressions();
          lhs = args[0];
          rhs = args[1];
        }
      }
      if (!ExpressionUtils.isEvaluatedAtCompileTime(lhs) && ExpressionUtils.isEvaluatedAtCompileTime(rhs)) {
        final PsiExpression temp = lhs;
        lhs = rhs;
        rhs = temp;
      }
      if (lhs == null || rhs == null) {
        return;
      }

      if (!assertHint.isExpectedActualOrder()) {
        final PsiExpression temp = lhs;
        lhs = rhs;
        rhs = temp;
      }

      final StringBuilder buf = new StringBuilder();
      final PsiType lhsType = lhs.getType();
      final PsiType rhsType = rhs.getType();
      if (lhsType != null && rhsType != null && PsiUtil.isLanguageLevel5OrHigher(lhs)) {
        final PsiPrimitiveType rhsUnboxedType = PsiPrimitiveType.getUnboxedType(rhsType);
        if (isPrimitiveAndBoxedWithOverloads(lhsType, rhsType) && rhsUnboxedType != null) {
          buf.append(lhs.getText()).append(",(").append(rhsUnboxedType.getCanonicalText()).append(')').append(rhs.getText());
        }
        else {
          final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(lhsType);
          if (isPrimitiveAndBoxedWithOverloads(rhsType, lhsType) && unboxedType != null) {
            buf.append('(').append(unboxedType.getCanonicalText()).append(')').append(lhs.getText()).append(',').append(rhs.getText());
          }
          else {
            buf.append(lhs.getText()).append(',').append(rhs.getText());
          }
        }
      }
      else {
        buf.append(lhs.getText()).append(',').append(rhs.getText());
      }

      final PsiExpression originalExpression = assertHint.getOriginalExpression();
      if (lhsType != null && TypeConversionUtil.isFloatOrDoubleType(lhsType.getDeepComponentType()) ||
          rhsType != null && TypeConversionUtil.isFloatOrDoubleType(rhsType.getDeepComponentType())) {
        final String noDelta = compoundMethodCall(methodName, assertHint, buf.toString());
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(originalExpression.getProject());
        final PsiExpression expression = methodName.equals("assertNotEquals")
                                         ? null
                                         : factory.createExpressionFromText(noDelta, originalExpression);
        final PsiMethod method = expression instanceof PsiMethodCallExpression ? ((PsiMethodCallExpression)expression).resolveMethod() : null;
        if (method == null || method.isDeprecated()) {
          buf.append(",0.0");
        }
      }
      final String newExpression = compoundMethodCall(methodName, assertHint, buf.toString());
      PsiReplacementUtil.replaceExpressionAndShorten(originalExpression, newExpression);
    }

    private boolean isPrimitiveAndBoxedWithOverloads(PsiType lhsType, PsiType rhsType) {
      if (lhsType instanceof PsiPrimitiveType && !PsiTypes.floatType().equals(lhsType) && !PsiTypes.doubleType().equals(lhsType)) {
        return rhsType instanceof PsiClassType;
      }
      return false;
    }

    private void replaceWithNegatedBooleanAssertion(AssertHint assertHint) {
      final PsiPrefixExpression expression = (PsiPrefixExpression)assertHint.getFirstArgument();
      final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());
      if (operand == null) {
        return;
      }
      final String newMethodName = assertHint.isAssertTrue() ? "assertFalse" : "assertTrue";
      final String newExpression = compoundMethodCall(newMethodName, assertHint, operand.getText());
      PsiReplacementUtil.replaceExpressionAndShorten(assertHint.getOriginalExpression(), newExpression);
    }

    private void replaceAssertWithAssertNull(AssertHint assertHint) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)assertHint.getFirstArgument();
      final PsiExpression lhs = binaryExpression.getLOperand();
      PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!ExpressionUtils.isEvaluatedAtCompileTime(lhs) && ExpressionUtils.isEvaluatedAtCompileTime(rhs)) {
        rhs = lhs;
      }
      @NonNls final String methodName = assertHint.getMethod().getName();
      @NonNls final String memberName;
      if ("assertFalse".equals(methodName) ^ tokenType.equals(JavaTokenType.NE)) {
        memberName = "assertNotNull";
      }
      else {
        memberName = "assertNull";
      }
      final String newExpression = compoundMethodCall(memberName, assertHint, rhs.getText());
      PsiReplacementUtil.replaceExpressionAndShorten(assertHint.getOriginalExpression(), newExpression);
    }

    private String compoundMethodCall(@NonNls String methodName, AssertHint assertHint, String args) {
      final PsiExpression message = assertHint.getMessage();
      final StringBuilder newExpression = new StringBuilder();
      addStaticImportOrQualifier(methodName, assertHint, newExpression);
      newExpression.append(methodName).append('(');
      final int index = assertHint.getArgIndex();
      if (message != null && index != 0) {
        newExpression.append(message.getText()).append(',');
      }
      newExpression.append(args);
      if (message != null && index == 0) {
        newExpression.append(',').append(message.getText());
      }
      newExpression.append(')');
      return newExpression.toString();
    }

    private void replaceWithAssertSame(AssertHint assertHint) {
      final PsiBinaryExpression firstArgument = (PsiBinaryExpression)assertHint.getFirstArgument();
      PsiExpression lhs = firstArgument.getLOperand();
      PsiExpression rhs = firstArgument.getROperand();
      final IElementType tokenType = firstArgument.getOperationTokenType();
      if (!ExpressionUtils.isEvaluatedAtCompileTime(lhs) && ExpressionUtils.isEvaluatedAtCompileTime(rhs)) {
        final PsiExpression temp = lhs;
        lhs = rhs;
        rhs = temp;
      }
      if (rhs == null) {
        return;
      }
      @NonNls final String methodName = assertHint.getMethod().getName();
      @NonNls final String memberName;
      if ("assertFalse".equals(methodName) ^ tokenType.equals(JavaTokenType.NE)) {
        memberName = "assertNotSame";
      }
      else {
        memberName = "assertSame";
      }
      final String newExpression = compoundMethodCall(memberName, assertHint, lhs.getText() + "," + rhs.getText());
      PsiReplacementUtil.replaceExpressionAndShorten(assertHint.getOriginalExpression(), newExpression);
    }

    private void replaceAssertEqualsWithAssertLiteral(AssertHint assertHint) {
      final PsiExpression firstTestArgument = assertHint.getFirstArgument();
      final PsiExpression secondTestArgument = assertHint.getSecondArgument();
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
      @NonNls final String methodName = "assert" + uppercaseLiteralValue;
      final String newExpression = compoundMethodCall(methodName, assertHint, compareValue);
      PsiReplacementUtil.replaceExpressionAndShorten(assertHint.getOriginalExpression(), newExpression);
    }
  }

  private class SimplifiableJUnitAssertionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final AssertHint assertHint = AssertHint.createAssertEqualsHint(expression);
      if (assertHint != null && isAssertEqualsThatCouldBeAssertLiteral(assertHint)) {
        registerMethodCallError(expression, getReplacementMethodName(assertHint));
      }
      else {
        final AssertHint assertTrueFalseHint = AssertHint.createAssertTrueFalseHint(expression);
        if (assertTrueFalseHint == null) {
          return;
        }

        final boolean assertTrue = assertTrueFalseHint.isAssertTrue();
        final PsiExpression firstArgument = assertTrueFalseHint.getFirstArgument();
        if (ComparisonUtils.isNullComparison(firstArgument)) {
          registerMethodCallError(expression, assertTrue == isEqEqExpression(firstArgument) ? "assertNull()" : "assertNotNull()");
        }
        else if (isIdentityComparison(firstArgument)) {
          registerMethodCallError(expression, assertTrue == isEqEqExpression(firstArgument) ? "assertSame()" : "assertNotSame()");
        }
        else {
          if (isEqualityComparison(firstArgument)) {
            if (assertTrue) {
              registerMethodCallError(expression, "assertEquals()");
            }
            else if (firstArgument instanceof PsiMethodCallExpression || hasPrimitiveOverload(assertTrueFalseHint)) {
              registerMethodCallError(expression, "assertNotEquals()");
            }
          }
          else if (isAssertThatCouldBeFail(firstArgument, !assertTrue)) {
            registerMethodCallError(expression, "fail()");
          }
          else if (assertTrue && assertTrueFalseHint.isExpectedActualOrder() && isArrayEqualityComparison(firstArgument)) {
            registerMethodCallError(expression, "assertArrayEquals()");
          }
          else if (BoolUtils.isNegation(firstArgument)) {
            registerMethodCallError(expression, assertTrue ? "assertFalse()" : "assertTrue()");
          }
        }
      }
    }

    private boolean hasPrimitiveOverload(AssertHint assertHint) {
      final PsiClass containingClass = assertHint.getMethod().getContainingClass();
      if (containingClass == null) {
        return false;
      }
      final PsiMethod primitiveOverload = CachedValuesManager.getCachedValue(containingClass, () -> {
        final PsiMethod patternMethod = JavaPsiFacade.getElementFactory(containingClass.getProject())
          .createMethodFromText("public static void assertNotEquals(long a, long b){}", containingClass);
        return new CachedValueProvider.Result<>(containingClass.findMethodBySignature(patternMethod, true),
                                                PsiModificationTracker.MODIFICATION_COUNT);
      });
      return primitiveOverload != null;
    }

    @NonNls
    private String getReplacementMethodName(AssertHint assertHint) {
      final PsiExpression firstArgument = assertHint.getFirstArgument();
      final PsiExpression secondArgument = assertHint.getSecondArgument();
      final PsiLiteralExpression literalExpression;
      if (firstArgument instanceof PsiLiteralExpression) {
        literalExpression = (PsiLiteralExpression)firstArgument;
      }
      else if (secondArgument instanceof PsiLiteralExpression) {
        literalExpression = (PsiLiteralExpression)secondArgument;
      }
      else {
        return "";
      }
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
      return "";
    }

    private boolean isEqEqExpression(PsiExpression argument) {
      if (!(argument instanceof PsiBinaryExpression binaryExpression)) {
        return false;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      return JavaTokenType.EQEQ.equals(tokenType);
    }
  }
}

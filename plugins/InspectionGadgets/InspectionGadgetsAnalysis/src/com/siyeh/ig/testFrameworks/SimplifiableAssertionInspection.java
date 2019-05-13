/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.testFrameworks;

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
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.EqualityCheck;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class SimplifiableAssertionInspection extends BaseInspection {
  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("simplifiable.junit.assertion.problem.descriptor", infos[0]);
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new SimplifyAssertFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SimplifiableJUnitAssertionVisitor();
  }

  protected abstract boolean checkTestNG();

  static boolean isAssertThatCouldBeFail(PsiExpression position, boolean checkTrue) {
    return (checkTrue ? PsiKeyword.TRUE : PsiKeyword.FALSE).equals(position.getText());
  }

  boolean isAssertEqualsThatCouldBeAssertLiteral(PsiMethodCallExpression expression) {
    final AssertHint assertHint = AssertHint.createAssertEqualsHint(expression, checkTestNG());
    if (assertHint == null) {
      return false;
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    final int argIndex = assertHint.getArgIndex();
    final PsiExpression firstTestArgument = arguments[argIndex];
    final PsiExpression secondTestArgument = arguments[argIndex + 1];
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
      return type != null && TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(type);
    }
    return EqualityCheck.from(expression) != null;
  }

  private static final CallMatcher ARRAYS_EQUALS = CallMatcher.staticCall("java.util.Arrays", "equals").parameterCount(2);
  private static boolean isArrayEqualityComparison(PsiExpression expression) {
    return expression instanceof PsiMethodCallExpression && ARRAYS_EQUALS.test((PsiMethodCallExpression)expression);
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

  private class SimplifyAssertFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("simplify.junit.assertion.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = methodNameIdentifier.getParent();
      if (parent == null) {
        return;
      }
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)parent.getParent();
      if (isAssertEqualsThatCouldBeAssertLiteral(callExpression)) {
        replaceAssertEqualsWithAssertLiteral(callExpression);
      }
      else {
        final AssertHint assertTrueFalseHint = AssertHint.createAssertTrueFalseHint(callExpression, checkTestNG());
        if (assertTrueFalseHint == null) {
          return;
        }
        final boolean assertTrue = isAssertTrue(assertTrueFalseHint);
        final PsiExpression position = assertTrueFalseHint.getPosition(callExpression.getArgumentList().getExpressions());
        if (ComparisonUtils.isNullComparison(position)) {
          replaceAssertWithAssertNull(callExpression, (PsiBinaryExpression)position, assertTrueFalseHint.getMessage(), assertTrueFalseHint.getArgIndex());
        }
        else if (isIdentityComparison(position)) {
          replaceAssertWithAssertSame(callExpression, (PsiBinaryExpression)position, assertTrueFalseHint.getMessage(), assertTrueFalseHint.getArgIndex());
        }
        else if (assertTrue && isEqualityComparison(position)) {
          replaceAssertLiteralWithAssertEquals(callExpression, position, assertTrueFalseHint.getMessage(), assertTrueFalseHint.getArgIndex(), "assertEquals");
        }
        else if (isAssertThatCouldBeFail(position, !assertTrue)) {
          replaceAssertWithFail(callExpression, assertTrueFalseHint.getMessage());
        }
        else if (isEqualityComparison(position)) {
          replaceAssertLiteralWithAssertEquals(callExpression, position, assertTrueFalseHint.getMessage(), assertTrueFalseHint.getArgIndex(), "assertNotEquals");
        }
        else if (assertTrue && isArrayEqualityComparison(position)) {
          replaceAssertLiteralWithAssertEquals(callExpression, position, assertTrueFalseHint.getMessage(), assertTrueFalseHint.getArgIndex(), "assertArrayEquals");
        }
        else if (BoolUtils.isNegation(position)) {
          replaceWithNegatedBooleanAssertion(callExpression, (PsiPrefixExpression) position, assertTrue ? "assertFalse" : "assertTrue");
        }
      }
    }

    private void addStaticImportOrQualifier(String methodName, PsiMethodCallExpression originalMethodCall, StringBuilder out) {
      final PsiReferenceExpression methodExpression = originalMethodCall.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        final PsiMethod method = originalMethodCall.resolveMethod();
        if (method == null) {
          return;
        }
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

    private void replaceAssertWithFail(PsiMethodCallExpression callExpression, PsiExpression message) {
      @NonNls final StringBuilder newExpression = new StringBuilder();
      addStaticImportOrQualifier("fail", callExpression, newExpression);
      newExpression.append("fail(");
      if (message != null) {
        newExpression.append(message.getText());
      }
      newExpression.append(')');
      PsiReplacementUtil.replaceExpressionAndShorten(callExpression, newExpression.toString());
    }

    /**
     * <code>assertTrue</code> -> <code>assertEquals</code>
     * <p/
     * <code>assertFalse</code> -> <code>assertNotEquals</code> (do not replace for junit 5 Assertions
     * as there is no primitive overloads for <code>assertNotEquals</code> and boxing would be enforced if replaced)
     */
    private void replaceAssertLiteralWithAssertEquals(PsiMethodCallExpression callExpression,
                                                      final PsiExpression position,
                                                      final PsiExpression message,
                                                      final int positionIndex,
                                                      final String methodName) {

      PsiExpression lhs = null;
      PsiExpression rhs = null;
      if (position instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)position;
        lhs = binaryExpression.getLOperand();
        rhs = binaryExpression.getROperand();
      }
      else {
        EqualityCheck check = EqualityCheck.from(position);
        if (check != null) {
          lhs = check.getLeft();
          rhs = check.getRight();
        }
        else if (position instanceof PsiMethodCallExpression && ARRAYS_EQUALS.test((PsiMethodCallExpression)position)) {
          PsiExpression[] args = ((PsiMethodCallExpression)position).getArgumentList().getExpressions();
          lhs = args[0];
          rhs = args[1];
        }
      }
      if (!(lhs instanceof PsiLiteralExpression) && rhs instanceof PsiLiteralExpression) {
        final PsiExpression temp = lhs;
        lhs = rhs;
        rhs = temp;
      }
      if (lhs == null || rhs == null) {
        return;
      }

      if (checkTestNG()) {
        final PsiExpression temp = lhs;
        lhs = rhs;
        rhs = temp;
      }

      @NonNls final StringBuilder newExpression = new StringBuilder();
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

      if (lhsType != null && TypeConversionUtil.isFloatOrDoubleType(lhsType.getDeepComponentType()) ||
          rhsType != null && TypeConversionUtil.isFloatOrDoubleType(rhsType.getDeepComponentType()) ||
          isPrimitiveAndBoxedFloat(lhsType, rhsType) || isPrimitiveAndBoxedFloat(rhsType, lhsType)) {
        StringBuilder noDelta = new StringBuilder();
        compoundMethodCall(callExpression, methodName, message, positionIndex, buf.toString(), noDelta);
        PsiExpression expression = methodName.equals("assertNotEquals")
                                   ? null
                                   : JavaPsiFacade.getElementFactory(callExpression.getProject()).createExpressionFromText(noDelta.toString(), callExpression);
        PsiMethod method = expression instanceof PsiMethodCallExpression ? ((PsiMethodCallExpression)expression).resolveMethod() : null;
        if (method == null || method.isDeprecated()) {
          buf.append(",0.0");
        }
      }
      compoundMethodCall(callExpression, methodName, message, positionIndex, buf.toString(), newExpression);
      PsiReplacementUtil.replaceExpressionAndShorten(callExpression, newExpression.toString());
    }

    private boolean isPrimitiveAndBoxedWithOverloads(PsiType lhsType, PsiType rhsType) {
      if (lhsType instanceof PsiPrimitiveType && !PsiType.FLOAT.equals(lhsType) && !PsiType.DOUBLE.equals(lhsType)) {
        return rhsType instanceof PsiClassType;
      }
      return false;
    }

    private boolean isPrimitiveAndBoxedFloat(PsiType lhsType, PsiType rhsType) {
      return lhsType instanceof PsiPrimitiveType && rhsType instanceof PsiClassType &&
             (PsiType.DOUBLE.equals(rhsType) && PsiType.FLOAT.equals(rhsType));
    }

    private void replaceWithNegatedBooleanAssertion(PsiMethodCallExpression callExpression, PsiPrefixExpression expression, String newMethodName) {
      PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());
      if (operand == null) {
        return;
      }
      StringBuilder builder = new StringBuilder();
      addStaticImportOrQualifier(newMethodName, callExpression, builder);
      builder.append(newMethodName).append("(").append(operand.getText()).append(")");
      PsiReplacementUtil.replaceExpressionAndShorten(callExpression, builder.toString());
    }

    private void replaceAssertWithAssertNull(PsiMethodCallExpression callExpression,
                                             final PsiBinaryExpression binaryExpression,
                                             final PsiExpression message,
                                             final int positionIndex) {
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
      compoundMethodCall(callExpression, memberName, message, positionIndex, rhs.getText(), newExpression);
      PsiReplacementUtil.replaceExpressionAndShorten(callExpression, newExpression.toString());
    }

    private void compoundMethodCall(PsiMethodCallExpression callExpression, String methodName, PsiExpression message,
                                           int positionIndex,
                                           String args,
                                           StringBuilder newExpression) {
      addStaticImportOrQualifier(methodName, callExpression, newExpression);
      newExpression.append(methodName).append('(');
      if (message != null && positionIndex != 0) {
        newExpression.append(message.getText()).append(',');
      }
      newExpression.append(args);
      if (message != null && positionIndex == 0) {
        newExpression.append(',').append(message.getText());
      }
      newExpression.append(')');
    }

    private void replaceAssertWithAssertSame(PsiMethodCallExpression callExpression,
                                                    final PsiBinaryExpression position,
                                                    final PsiExpression message,
                                                    final int positionIndex) {
      PsiExpression lhs = position.getLOperand();
      PsiExpression rhs = position.getROperand();
      final IElementType tokenType = position.getOperationTokenType();
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
      compoundMethodCall(callExpression, memberName, message, positionIndex, lhs.getText() + "," + rhs.getText(), newExpression);
      PsiReplacementUtil.replaceExpressionAndShorten(callExpression, newExpression.toString());
    }

    private void replaceAssertEqualsWithAssertLiteral(PsiMethodCallExpression callExpression) {
      final AssertHint assertHint = AssertHint.createAssertEqualsHint(callExpression, checkTestNG());
      if (assertHint == null) return;

      final PsiExpressionList argumentList = callExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final int argIndex = assertHint.getArgIndex();
      final PsiExpression firstTestArgument = arguments[argIndex];
      final PsiExpression secondTestArgument = arguments[argIndex + 1];
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
      compoundMethodCall(callExpression, methodName, assertHint.getMessage(), argIndex, compareValue, newExpression);
      PsiReplacementUtil.replaceExpressionAndShorten(callExpression, newExpression.toString());
    }
  }

  private static boolean isAssertTrue(AssertHint assertTrueFalseHint) {
    return "assertTrue".equals(assertTrueFalseHint.getMethod().getName());
  }

  private class SimplifiableJUnitAssertionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (isAssertEqualsThatCouldBeAssertLiteral(expression)) {
        registerMethodCallError(expression, getReplacementMethodName(expression));
      }
      else {

        final AssertHint assertTrueFalseHint = AssertHint.createAssertTrueFalseHint(expression, checkTestNG());
        if (assertTrueFalseHint == null) {
          return;
        }

        final boolean assertTrue = isAssertTrue(assertTrueFalseHint);
        final PsiExpression position = assertTrueFalseHint.getPosition(expression.getArgumentList().getExpressions());
        if (ComparisonUtils.isNullComparison(position)) {
          registerMethodCallError(expression, assertTrue == hasEqEqExpressionArgument(position) ? "assertNull()" : "assertNotNull()");
        }
        else if (isIdentityComparison(position)) {
          registerMethodCallError(expression, assertTrue == hasEqEqExpressionArgument(position) ? "assertSame()" : "assertNotSame()");
        }
        else {
          if (isEqualityComparison(position)) {
            if (assertTrue) {
              registerMethodCallError(expression, "assertEquals()");
            }
            else if (position instanceof PsiMethodCallExpression || hasPrimitiveOverload(expression)) {
              registerMethodCallError(expression, "assertNotEquals()");
            }
          }
          else if (isAssertThatCouldBeFail(position, !assertTrue)) {
            registerMethodCallError(expression, "fail()");
          }
          else if (assertTrue && isArrayEqualityComparison(position)) {
            registerMethodCallError(expression, "assertArrayEquals");
          }
          else if (BoolUtils.isNegation(position)) {
            registerMethodCallError(expression, assertTrue ? "assertFalse()" :"assertTrue()");
          }
        }
      }
    }

    private boolean hasPrimitiveOverload(PsiMethodCallExpression expression) {
      PsiMethod method = expression.resolveMethod();
      if (method == null) return false;
      PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        PsiMethod primitiveOverload = CachedValuesManager.getCachedValue(containingClass, () -> {
          PsiMethod patternMethod = JavaPsiFacade.getElementFactory(containingClass.getProject())
            .createMethodFromText("public static void assertNotEquals(long a, long b){}", containingClass);
          return new CachedValueProvider.Result<>(containingClass.findMethodBySignature(patternMethod, true),
                                                  PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
        });
        return primitiveOverload != null;
      }
      return false;
    }

    @NonNls
    private String getReplacementMethodName(PsiMethodCallExpression expression) {
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression firstArgument = arguments[0];
      final PsiExpression secondArgument = arguments[1];
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

    private boolean hasEqEqExpressionArgument(PsiExpression argument) {
      if (!(argument instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)argument;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      return JavaTokenType.EQEQ.equals(tokenType);
    }
  }
}

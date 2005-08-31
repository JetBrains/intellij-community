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
package com.siyeh.ig.junit;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class SimplifiableJUnitAssertionInspection extends ExpressionInspection {

  private final SimplifyJUnitAssertFix fix = new SimplifyJUnitAssertFix();

  public String getGroupDisplayName() {
    return GroupNames.JUNIT_GROUP_NAME;
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class SimplifyJUnitAssertFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("simplify.j.unit.assertion.simplify.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = methodNameIdentifier.getParent();
      assert parent != null;
      final PsiMethodCallExpression callExpression =
        (PsiMethodCallExpression)parent .getParent();
      if (isAssertTrueThatCouldBeAssertEquality(callExpression)) {
        replaceAssertTrueWithAssertEquals(callExpression, project);
      }
      else if (isAssertEqualsThatCouldBeAssertLiteral(callExpression)) {
        replaceAssertEqualsWithAssertLiteral(callExpression, project);
      }
      else if (isAssertTrueThatCouldBeFail(callExpression)) {
        replaceAssertWithFail(callExpression);
      }
      else if (isAssertFalseThatCouldBeFail(callExpression)) {
        replaceAssertWithFail(callExpression);
      }
    }

    private void replaceAssertWithFail(PsiMethodCallExpression callExpression)
      throws IncorrectOperationException {
      final PsiReferenceExpression methodExpression =
        callExpression.getMethodExpression();

      final PsiMethod method = (PsiMethod)methodExpression.resolve();
      assert method != null;
      final PsiParameterList paramList = method.getParameterList();
      final PsiParameter[] parameters = paramList.getParameters();

      final PsiExpressionList argumentList =
        callExpression.getArgumentList();

      assert argumentList != null;
      final PsiExpression[] args = argumentList.getExpressions();
      final PsiExpression message;
      if (parameters.length == 2) {
        message = args[0];
      }
      else {
        message = null;
      }

      @NonNls final StringBuffer newExpression =
        new StringBuffer();
      newExpression.append("fail(");
      if (message != null) {
        newExpression.append(message.getText());
      }
      newExpression.append(')');
      replaceExpression(callExpression,
                        newExpression.toString());
    }

    private void replaceAssertTrueWithAssertEquals(PsiMethodCallExpression callExpression,
                                                   Project project)
      throws IncorrectOperationException {
      final PsiReferenceExpression methodExpression =
        callExpression.getMethodExpression();

      final PsiMethod method = (PsiMethod)methodExpression.resolve();
      assert method != null;
      final PsiParameterList paramList = method.getParameterList();
      final PsiParameter[] parameters = paramList.getParameters();

      final PsiManager psiManager = callExpression.getManager();

      final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      final PsiType stringType =
        PsiType.getJavaLangString(psiManager, scope);
      final PsiType paramType1 = parameters[0].getType();
      final PsiExpressionList argumentList =
        callExpression.getArgumentList();

      assert argumentList != null;
      final PsiExpression[] args = argumentList.getExpressions();
      final int testPosition;
      final PsiExpression message;
      if (paramType1.equals(stringType) && parameters.length >= 2) {
        testPosition = 1;
        message = args[0];
      }
      else {
        testPosition = 0;
        message = null;
      }
      final PsiExpression testArg = args[testPosition];

      PsiExpression lhs = null;
      PsiExpression rhs = null;
      if (testArg instanceof PsiBinaryExpression) {
        lhs = ((PsiBinaryExpression)testArg).getLOperand();
        rhs = ((PsiBinaryExpression)testArg).getROperand();
      }
      else if (testArg instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression call =
          (PsiMethodCallExpression)testArg;
        final PsiReferenceExpression equalityMethodExpression =
          call.getMethodExpression();
        final PsiExpressionList equalityArgumentList =
          call.getArgumentList();
        assert equalityArgumentList != null;
        final PsiExpression[] equalityArgs =
          equalityArgumentList.getExpressions();
        rhs = equalityArgs[0];
        lhs = equalityMethodExpression.getQualifierExpression();
      }
      if (!(lhs instanceof PsiLiteralExpression) &&
          rhs instanceof PsiLiteralExpression) {
        final PsiExpression temp = lhs;
        lhs = rhs;
        rhs = temp;
      }
      @NonNls final StringBuffer newExpression =
        new StringBuffer();
      newExpression.append("assertEquals(");
      if (message != null) {
        newExpression.append(message.getText());
        newExpression.append(',');
      }
      assert lhs != null;
      newExpression.append(lhs.getText());
      newExpression.append(',');
      assert rhs != null;
      newExpression.append(rhs.getText());
      if (isFloatingPoint(lhs) || isFloatingPoint(rhs)) {
        newExpression.append(",0.0");
      }
      newExpression.append(')');
      replaceExpression(callExpression,
                        newExpression.toString());
    }

    private void replaceAssertEqualsWithAssertLiteral(PsiMethodCallExpression callExpression,
                                                      Project project)
      throws IncorrectOperationException {
      final PsiReferenceExpression methodExpression =
        callExpression.getMethodExpression();

      final PsiMethod method = (PsiMethod)methodExpression.resolve();
      assert method != null;
      final PsiParameterList paramList = method.getParameterList();
      final PsiParameter[] parameters = paramList.getParameters();

      final PsiManager psiManager = callExpression.getManager();

      final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      final PsiType stringType =
        PsiType.getJavaLangString(psiManager, scope);
      final PsiType paramType1 = parameters[0].getType();
      final PsiExpressionList argumentList =
        callExpression.getArgumentList();

      assert argumentList != null;
      final PsiExpression[] args = argumentList.getExpressions();
      final int firstTestPosition;
      final int secondTestPosition;
      final PsiExpression message;
      if (paramType1.equals(stringType) && parameters.length >= 3) {
        firstTestPosition = 1;
        secondTestPosition = 2;
        message = args[0];
      }
      else {
        firstTestPosition = 0;
        secondTestPosition = 1;
        message = null;
      }
      final PsiExpression firstTestArg = args[firstTestPosition];
      final PsiExpression secondTestArg = args[secondTestPosition];
      final String literalValue;
      final String compareValue;
      if (isSimpleLiteral(firstTestArg)) {
        literalValue = firstTestArg.getText();
        compareValue = secondTestArg.getText();
      }
      else {
        literalValue = secondTestArg.getText();
        compareValue = firstTestArg.getText();
      }
      final String uppercaseLiteralValue =
        Character.toUpperCase(literalValue.charAt(0)) +
        literalValue.substring(1);
      @NonNls final StringBuffer newExpression =
        new StringBuffer();
      newExpression.append("assert" + uppercaseLiteralValue + '(');
      if (message != null) {
        newExpression.append(message.getText());
        newExpression.append(',');
      }
      newExpression.append(compareValue);
      newExpression.append(')');
      replaceExpression(callExpression,
                        newExpression.toString());
    }

    private boolean isFloatingPoint(PsiExpression expression) {
      final PsiType type = expression.getType();
      return PsiType.FLOAT.equals(type) || PsiType.DOUBLE.equals(type);
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new SimplifiableJUnitAssertionVisitor();
  }

  private static class SimplifiableJUnitAssertionVisitor
    extends BaseInspectionVisitor {


    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (isAssertTrueThatCouldBeAssertEquality(expression)) {
        registerMethodCallError(expression);
      }
      else if (isAssertEqualsThatCouldBeAssertLiteral(expression)) {
        registerMethodCallError(expression);
      }
      else if (isAssertTrueThatCouldBeFail(expression)) {
        registerMethodCallError(expression);
      }
      else if (isAssertFalseThatCouldBeFail(expression)) {
        registerMethodCallError(expression);
      }
    }
  }

  private static boolean isAssertTrueThatCouldBeAssertEquality(PsiMethodCallExpression expression) {
    if (!isAssertTrue(expression)) {
      return false;
    }
    final PsiReferenceExpression methodExpression =
      expression.getMethodExpression();

    final PsiMethod method = (PsiMethod)methodExpression.resolve();
    if (method == null) {
      return false;
    }
    final PsiParameterList paramList = method.getParameterList();
    if (paramList == null) {
      return false;
    }
    final PsiParameter[] parameters = paramList.getParameters();

    final PsiManager psiManager = expression.getManager();

    final Project project = psiManager.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiType stringType =
      PsiType.getJavaLangString(psiManager, scope);
    final PsiType paramType1 = parameters[0].getType();
    final int testPosition;
    if (paramType1.equals(stringType) && parameters.length > 1) {
      testPosition = 1;
    }
    else {
      testPosition = 0;
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    if (argumentList == null) {
      return false;
    }
    final PsiExpression[] args = argumentList.getExpressions();
    final PsiExpression testArg = args[testPosition];
    if (testArg == null) {
      return false;
    }
    return isEqualityComparison(testArg);
  }

  private static boolean isAssertTrueThatCouldBeFail(PsiMethodCallExpression expression) {
    if (!isAssertTrue(expression)) {
      return false;
    }
    final PsiReferenceExpression methodExpression =
      expression.getMethodExpression();

    final PsiMethod method = (PsiMethod)methodExpression.resolve();
    if (method == null) {
      return false;
    }
    final PsiParameterList paramList = method.getParameterList();
    if (paramList == null) {
      return false;
    }
    final PsiParameter[] parameters = paramList.getParameters();

    final PsiManager psiManager = expression.getManager();

    final Project project = psiManager.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiType stringType =
      PsiType.getJavaLangString(psiManager, scope);
    final PsiType paramType1 = parameters[0].getType();
    final int testPosition;
    if (paramType1.equals(stringType) && parameters.length > 1) {
      testPosition = 1;
    }
    else {
      testPosition = 0;
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    if (argumentList == null) {
      return false;
    }
    final PsiExpression[] args = argumentList.getExpressions();
    final PsiExpression testArg = args[testPosition];
    if (testArg == null) {
      return false;
    }
    return PsiKeyword.FALSE.equals(testArg.getText());
  }

  private static boolean isAssertFalseThatCouldBeFail(PsiMethodCallExpression expression) {
    if (!isAssertFalse(expression)) {
      return false;
    }
    final PsiReferenceExpression methodExpression =
      expression.getMethodExpression();

    final PsiMethod method = (PsiMethod)methodExpression.resolve();
    if (method == null) {
      return false;
    }
    final PsiParameterList paramList = method.getParameterList();
    if (paramList == null) {
      return false;
    }
    final PsiParameter[] parameters = paramList.getParameters();

    final PsiManager psiManager = expression.getManager();

    final Project project = psiManager.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiType stringType =
      PsiType.getJavaLangString(psiManager, scope);
    final PsiType paramType1 = parameters[0].getType();
    final int testPosition;
    if (paramType1.equals(stringType) && parameters.length > 1) {
      testPosition = 1;
    }
    else {
      testPosition = 0;
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    if (argumentList == null) {
      return false;
    }
    final PsiExpression[] args = argumentList.getExpressions();
    final PsiExpression testArg = args[testPosition];
    if (testArg == null) {
      return false;
    }
    return PsiKeyword.TRUE.equals(testArg.getText());
  }

  private static boolean isAssertEqualsThatCouldBeAssertLiteral(PsiMethodCallExpression expression) {
    if (!isAssertEquals(expression)) {
      return false;
    }
    final PsiReferenceExpression methodExpression =
      expression.getMethodExpression();

    final PsiMethod method = (PsiMethod)methodExpression.resolve();
    if (method == null) {
      return false;
    }
    final PsiParameterList paramList = method.getParameterList();
    if (paramList == null) {
      return false;
    }
    final PsiParameter[] parameters = paramList.getParameters();

    final PsiManager psiManager = expression.getManager();

    final Project project = psiManager.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiType stringType =
      PsiType.getJavaLangString(psiManager, scope);
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
    if (argumentList == null) {
      return false;
    }
    final PsiExpression[] args = argumentList.getExpressions();
    final PsiExpression firstTestArg = args[firstTestPosition];
    final PsiExpression secondTestArg = args[secondTestPosition];
    if (firstTestArg == null) {
      return false;
    }
    if (secondTestArg == null) {
      return false;
    }
    return isSimpleLiteral(firstTestArg) || isSimpleLiteral(secondTestArg);
  }

  private static boolean isSimpleLiteral(PsiExpression arg) {
    if (!(arg instanceof PsiLiteralExpression)) {
      return false;
    }
    final String text = arg.getText();
    return PsiKeyword.NULL.equals(text) || PsiKeyword.TRUE.equals(text) ||
           PsiKeyword.FALSE.equals(text);
  }

  private static boolean isEqualityComparison(PsiExpression testArg) {
    if (testArg instanceof PsiBinaryExpression) {
      final PsiJavaToken sign =
        ((PsiBinaryExpression)testArg).getOperationSign();
      if (!sign.getTokenType().equals(JavaTokenType.EQEQ)) {
        return false;
      }
      final PsiExpression lhs =
        ((PsiBinaryExpression)testArg).getLOperand();
      final PsiExpression rhs =
        ((PsiBinaryExpression)testArg).getROperand();
      if (rhs == null) {
        return false;
      }
      final PsiType type = lhs.getType();
      if (type == null) {
        return false;
      }
      return ClassUtils.isPrimitive(type);
    }
    else if (testArg instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression call =
        (PsiMethodCallExpression)testArg;
      final PsiReferenceExpression methodExpression =
        call.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.EQUALS.equals(methodName)) {
        return false;
      }
      final PsiExpressionList argumentList = call.getArgumentList();
      if (argumentList == null) {
        return false;
      }
      final PsiExpression[] args = argumentList.getExpressions();
      if (args == null) {
        return false;
      }
      if (args.length != 1) {
        return false;
      }
      if (args[0] == null) {
        return false;
      }
      return methodExpression.getQualifierExpression() != null;
    }
    return false;
  }

  private static boolean isAssertTrue(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression =
      expression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!"assertTrue".equals(methodName)) {
      return false;
    }
    final PsiMethod method = (PsiMethod)methodExpression.resolve();
    if (method == null) {
      return false;
    }

    final PsiClass targetClass = method.getContainingClass();
    return targetClass != null &&
           ClassUtils.isSubclass(targetClass,
                                 "junit.framework.Assert");
  }

  private static boolean isAssertFalse(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression =
      expression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!"assertFalse".equals(methodName)) {
      return false;
    }
    final PsiMethod method = (PsiMethod)methodExpression.resolve();
    if (method == null) {
      return false;
    }

    final PsiClass targetClass = method.getContainingClass();
    return targetClass != null &&
           ClassUtils.isSubclass(targetClass,
                                 "junit.framework.Assert");
  }

  private static boolean isAssertEquals(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression =
      expression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!"assertEquals".equals(methodName)) {
      return false;
    }
    final PsiMethod method = (PsiMethod)methodExpression.resolve();
    if (method == null) {
      return false;
    }

    final PsiClass targetClass = method.getContainingClass();
    return targetClass != null &&
           ClassUtils.isSubclass(targetClass,
                                 "junit.framework.Assert");
  }
}

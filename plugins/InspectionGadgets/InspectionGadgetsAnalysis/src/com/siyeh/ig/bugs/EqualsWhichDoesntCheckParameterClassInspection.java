/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public class EqualsWhichDoesntCheckParameterClassInspection extends BaseInspection {
  private static final CallMatcher REFLECTION_EQUALS =
    CallMatcher.staticCall("org.apache.commons.lang.builder.EqualsBuilder", "reflectionEquals");
  private static final CallMatcher CLASS_IS_INSTANCE =
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_CLASS, "isInstance").parameterCount(1);
  private static final CallMatcher OBJECT_GET_CLASS =
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "getClass").parameterCount(0);

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("equals.doesnt.check.class.parameter.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("equals.doesnt.check.class.parameter.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsWhichDoesntCheckParameterClassVisitor();
  }

  private static class EqualsWhichDoesntCheckParameterClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      // note: no call to super
      if (!MethodUtils.isEquals(method)) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiParameter parameter = parameters[0];
      final PsiCodeBlock body = method.getBody();
      if (body == null || isParameterChecked(body, parameter) || isParameterCheckNotNeeded(body, parameter)) {
        return;
      }
      registerMethodError(method);
    }

    private static boolean isParameterChecked(PsiCodeBlock body, PsiParameter parameter) {
      final ParameterClassCheckVisitor visitor = new ParameterClassCheckVisitor(parameter);
      body.accept(visitor);
      return visitor.isChecked();
    }

    private static boolean isParameterCheckNotNeeded(PsiCodeBlock body, PsiParameter parameter) {
      if (ControlFlowUtils.isEmptyCodeBlock(body)) {
        return true; // incomplete code
      }
      final PsiStatement statement = ControlFlowUtils.getOnlyStatementInBlock(body);
      if (statement == null) {
        return false;
      }
      if (!(statement instanceof PsiReturnStatement)) {
        return true; // incomplete code
      }
      final PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
      final PsiExpression returnValue = returnStatement.getReturnValue();
      final Object constant = ExpressionUtils.computeConstantExpression(returnValue);
      if (Boolean.FALSE.equals(constant)) {
        return true; // incomplete code
      }
      if (REFLECTION_EQUALS.matches(returnValue)) {
        return true;
      }
      if (isIdentityEquals(returnValue, parameter)) {
        return true;
      }
      return false;
    }

    private static boolean isIdentityEquals(PsiExpression expression, PsiParameter parameter) {
      if (!(expression instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      return isIdentityEquals(lhs, rhs, parameter) || isIdentityEquals(rhs, lhs, parameter);
    }

    private static boolean isIdentityEquals(PsiExpression lhs, PsiExpression rhs, PsiParameter parameter) {
      return ExpressionUtils.isReferenceTo(lhs, parameter) &&
             rhs instanceof PsiThisExpression &&
             ((PsiThisExpression)rhs).getQualifier() == null;
    }
  }

  private static class ParameterClassCheckVisitor extends JavaRecursiveElementWalkingVisitor {
    private final PsiParameter myParameter;
    private boolean myChecked;

    ParameterClassCheckVisitor(@NotNull PsiParameter parameter) {
      myParameter = parameter;
    }

    private void makeChecked() {
      myChecked = true;
      stopWalking();
    }

    @Contract("null -> false")
    private boolean isParameterReference(PsiExpression operand) {
      PsiReferenceExpression ref = tryCast(PsiUtil.skipParenthesizedExprDown(operand), PsiReferenceExpression.class);
      if (ref == null) return false;
      PsiParameter target = tryCast(ref.resolve(), PsiParameter.class);
      if (target == myParameter) return true;
      if (target == null) return false;
      return target.getParent() instanceof PsiParameterList && target.getParent().getParent() instanceof PsiLambdaExpression;
    }

    private boolean isGetInstanceCall(PsiMethodCallExpression call) {
      if (!CLASS_IS_INSTANCE.test(call)) return false;
      final PsiExpression arg = call.getArgumentList().getExpressions()[0];
      return isParameterReference(arg);
    }

    private boolean isGetClassCall(PsiMethodCallExpression call) {
      if (!OBJECT_GET_CLASS.test(call)) return false;
      final PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      return isParameterReference(qualifier);
    }

    private boolean isCallToSuperEquals(PsiMethodCallExpression call) {
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (!(qualifierExpression instanceof PsiSuperExpression)) return false;
      final String name = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.EQUALS.equals(name)) return false;
      final PsiExpression[] arguments = call.getArgumentList().getExpressions();
      if (arguments.length != 1) return false;
      return isParameterReference(arguments[0]);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (isGetClassCall(expression) || isGetInstanceCall(expression) || isCallToSuperEquals(expression)) {
        makeChecked();
      }
    }

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      super.visitMethodReferenceExpression(expression);
      if (CLASS_IS_INSTANCE.methodReferenceMatches(expression)) {
        makeChecked();
      }
    }

    @Override
    public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
      super.visitInstanceOfExpression(expression);
      if (isParameterReference(expression.getOperand())) {
        makeChecked();
      }
    }

    @Override
    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      final PsiExpression operand = expression.getOperand();
      if (!isParameterReference(operand)) return;
      final PsiTryStatement statement = PsiTreeUtil.getParentOfType(expression, PsiTryStatement.class);
      if (statement == null) return;
      final PsiParameter[] parameters = statement.getCatchBlockParameters();
      if (parameters.length < 2) return;
      boolean nullPointerExceptionFound = false;
      boolean classCastExceptionFound = false;
      for (PsiParameter parameter : parameters) {
        final PsiType type = parameter.getType();
        if (type.equalsToText("java.lang.NullPointerException")) {
          nullPointerExceptionFound = true;
          if (classCastExceptionFound) break;
        }
        else if (type.equalsToText("java.lang.ClassCastException")) {
          classCastExceptionFound = true;
          if (nullPointerExceptionFound) break;
        }
      }
      if (classCastExceptionFound && nullPointerExceptionFound) {
        makeChecked();
      }
    }

    public boolean isChecked() {
      return myChecked;
    }
  }
}
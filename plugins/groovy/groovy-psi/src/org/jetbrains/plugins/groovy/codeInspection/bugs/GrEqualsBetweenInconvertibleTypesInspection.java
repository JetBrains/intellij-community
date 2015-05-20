/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public class GrEqualsBetweenInconvertibleTypesInspection extends BaseInspection {

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new MyVisitor();
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... args) {
    return GroovyInspectionBundle.message("equals.between.inconvertible.types.tooltip", args[0], args[1], args[2]);
  }

  private static class MyVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
      super.visitMethodCallExpression(methodCallExpression);
      processMethodCall(methodCallExpression);
    }

    @Override
    public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
      super.visitApplicationStatement(applicationStatement);
      processMethodCall(applicationStatement);
    }

    @Override
    public void visitBinaryExpression(GrBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (expression.getOperationTokenType() != GroovyTokenTypes.mEQUAL) return;
      if (expression.getRightOperand() == null) return;
      final PsiType rightType = expression.getRightOperand().getType();
      final PsiType leftType = expression.getLeftOperand().getType();
      if (rightType == null || leftType == null) return;
      if (TypeUtils.areConvertible(rightType, leftType)) return;
      registerError(expression, "==", leftType.getPresentableText(), rightType.getPresentableText());
    }

    private void processMethodCall(GrMethodCall methodCall) {
      final PsiMethod method = methodCall.resolveMethod();
      if (method == null || !method.getName().equals("equals")) return;

      final GrArgumentList argumentList = methodCall.getArgumentList();
      final GrExpression[] arguments = argumentList.getExpressionArguments();
      if (arguments.length != 1) return;
      final PsiType rightType = arguments[0].getType();
      if (rightType == null) return;

      if (method.hasModifierProperty(PsiModifier.STATIC)) return;
      if (!MethodUtils.isEquals(method)) return;
      assert methodCall.getInvokedExpression() instanceof GrReferenceExpression;
      final GrReferenceExpression methodExpression = (GrReferenceExpression)methodCall.getInvokedExpression();
      final PsiType leftType = PsiImplUtil.getQualifierType(methodExpression);
      if (leftType == null) return;

      if (TypeUtils.areConvertible(rightType, leftType)) return;
      registerMethodCallError(methodCall, "equals()", leftType.getPresentableText(), rightType.getPresentableText());
    }
  }
}

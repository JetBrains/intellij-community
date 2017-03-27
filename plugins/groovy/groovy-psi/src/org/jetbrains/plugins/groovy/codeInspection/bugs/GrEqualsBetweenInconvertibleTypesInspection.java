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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.psi.util.InheritanceUtil.isInheritor;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_GSTRING;

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
    public void visitBinaryExpression(@NotNull GrBinaryExpression expression) {
      if (expression.getOperationTokenType() != GroovyTokenTypes.mEQUAL) return;

      PsiType leftType = expression.getLeftType();
      if (leftType == null) return;

      PsiType rightType = expression.getRightType();
      if (rightType == null) return;

      if (TypeUtils.areConvertible(rightType, leftType)) return;
      if (isInheritor(leftType, JAVA_LANG_NUMBER)) {
        if (isInheritor(rightType, JAVA_LANG_NUMBER)) return;
        if (isInheritor(rightType, JAVA_LANG_CHARACTER)) return;
        if (isInheritor(rightType, JAVA_LANG_STRING)) return; // Number == "1"
      }
      else if (isInheritor(leftType, JAVA_LANG_CHARACTER)) {
        if (isInheritor(rightType, JAVA_LANG_NUMBER)) return;
        if (isInheritor(rightType, JAVA_LANG_STRING)) return; // Character == "1"
      }
      else if (isInheritor(leftType, JAVA_LANG_STRING)) {
        if (isInheritor(rightType, JAVA_LANG_NUMBER)) return; // "1" : Number
        if (isInheritor(rightType, JAVA_LANG_CHARACTER)) return;
        if (isInheritor(rightType, GROOVY_LANG_GSTRING)) return;
      }
      else if (isInheritor(leftType, GROOVY_LANG_GSTRING)) {
        if (isInheritor(rightType, JAVA_LANG_STRING)) return;
      }

      registerError(expression.getOperationToken(), "==", leftType.getPresentableText(), rightType.getPresentableText());
    }

    @Override
    public void visitMethodCallExpression(@NotNull GrMethodCallExpression methodCallExpression) {
      processMethodCall(methodCallExpression);
    }

    @Override
    public void visitApplicationStatement(@NotNull GrApplicationStatement applicationStatement) {
      processMethodCall(applicationStatement);
    }

    private void processMethodCall(GrMethodCall methodCall) {
      GrExpression invokedExpression = methodCall.getInvokedExpression();
      if (!(invokedExpression instanceof GrReferenceExpression)) return;

      String name = ((GrReferenceExpression)invokedExpression).getReferenceName();
      if (!"equals".equals(name)) return;

      final PsiType leftType = PsiImplUtil.getQualifierType((GrReferenceExpression)invokedExpression);
      if (leftType == null) return;

      PsiType[] argumentTypes = PsiUtil.getArgumentTypes(methodCall.getArgumentList());
      if (argumentTypes == null || argumentTypes.length != 1) return;

      PsiType rightType = argumentTypes[0];
      if (rightType == null) return;

      final PsiMethod method = methodCall.resolveMethod();
      if (!MethodUtils.isEquals(method)) return;
      if (method.hasModifierProperty(PsiModifier.STATIC)) return;

      if (TypeUtils.areConvertible(rightType, leftType)) return;
      registerMethodCallError(methodCall, "equals()", leftType.getPresentableText(), rightType.getPresentableText());
    }
  }
}

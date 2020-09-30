// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.psi.util.InheritanceUtil.isInheritor;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets.EQUALITY_OPERATORS;
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
    return GroovyBundle.message("equals.between.inconvertible.types.tooltip", args[0], args[1], args[2]);
  }

  private static class MyVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull GrBinaryExpression expression) {
      IElementType tokenType = expression.getOperationTokenType();
      if (!EQUALITY_OPERATORS.contains(tokenType)) return;

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

      registerError(expression.getOperationToken(), tokenType, leftType.getPresentableText(), rightType.getPresentableText());
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

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.secondUnsafeCall;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.inspections.SecondUnsafeCallQuickFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyLocalInspectionTool;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public class SecondUnsafeCallInspection extends GroovyLocalInspectionTool {

  @Override
  public @NotNull GroovyElementVisitor buildGroovyVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new GroovyElementVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull GrReferenceExpression refExpression) {
        checkForSecondUnsafeCall(refExpression, holder);
      }
    };
  }

  private static void checkForSecondUnsafeCall(GrExpression expression, ProblemsHolder holder) {
    checkForSecondUnsafeCall(expression, holder, null);
  }

  private static void checkForSecondUnsafeCall(GrExpression expression, ProblemsHolder holder, @Nullable PsiElement highlightElement) {
    if (highlightElement == null) highlightElement = expression;

    final GrReferenceExpression referenceExpression = (GrReferenceExpression)expression;

    if (GroovyTokenTypes.mDOT.equals(referenceExpression.getDotTokenType())) {
      if (isNullableQualifier(referenceExpression.getQualifierExpression())) {
        holder.registerProblem(highlightElement, GroovyBundle.message("call.can.throw.npe"), new SecondUnsafeCallQuickFix());
      }
    }
  }

  private static boolean isNullableQualifier(GrExpression qualifier) {
    GrExpression newQualifier = qualifier;
    while (newQualifier != null) {
      GrReferenceExpression innerRefExpr = getReferenceExpression(newQualifier);
      if (innerRefExpr == null) {
        return false;
      }
      IElementType dotToken = innerRefExpr.getDotTokenType();
      if (GroovyTokenTypes.mOPTIONAL_CHAIN_DOT.equals(dotToken)) {
        return false;
      } else {
        newQualifier = innerRefExpr.getQualifier();
      }
    }
    GrReferenceExpression topRefExpr = getReferenceExpression(qualifier);
    if (topRefExpr == null) {
      return false;
    }
    if (GroovyTokenTypes.mOPTIONAL_DOT.equals(topRefExpr.getDotTokenType())) {
      return true;
    }
    return false;
  }

  private static @Nullable GrReferenceExpression getReferenceExpression(GrExpression expression) {
    if (expression instanceof GrMethodCallExpression) {
      expression = ((GrMethodCallExpression)expression).getInvokedExpression();
    }
    return expression instanceof GrReferenceExpression ? (GrReferenceExpression)expression : null;
  }
}

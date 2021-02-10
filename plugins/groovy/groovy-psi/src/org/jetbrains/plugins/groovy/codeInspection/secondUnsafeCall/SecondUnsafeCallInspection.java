// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.secondUnsafeCall;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
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
      //        a?.b or a?.b()
      final GrExpression qualifier = referenceExpression.getQualifierExpression();
      //        a?.b()
      if (qualifier instanceof GrMethodCallExpression) {
        final GrExpression expression1 = ((GrMethodCallExpression)qualifier).getInvokedExpression();
        //        a?.b
        if (!(expression1 instanceof GrReferenceExpression)) return;

        if (GroovyTokenTypes.mOPTIONAL_DOT.equals(((GrReferenceExpression)expression1).getDotTokenType())) {
          holder.registerProblem(highlightElement, GroovyBundle.message("call.can.throw.npe"), new SecondUnsafeCallQuickFix());
        }
      }
      else
        //        a?.b
        if (qualifier instanceof GrReferenceExpression) {
          if (GroovyTokenTypes.mOPTIONAL_DOT.equals(((GrReferenceExpression)qualifier).getDotTokenType())) {
            holder.registerProblem(highlightElement, GroovyBundle.message("call.can.throw.npe"), new SecondUnsafeCallQuickFix());
          }
        }
    }
  }
}

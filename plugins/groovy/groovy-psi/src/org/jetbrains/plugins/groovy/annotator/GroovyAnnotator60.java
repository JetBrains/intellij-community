// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrDeferStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrYieldReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAsyncExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;

/**
 * @author Bas Leijdekkers
 */
final class GroovyAnnotator60 extends GroovyElementVisitor {
  private final AnnotationHolder myHolder;

  GroovyAnnotator60(@NotNull AnnotationHolder holder) {
    myHolder = holder;
  }

  @Override
  public void visitDeferStatement(@NotNull GrDeferStatement deferStatement) {
    if (PsiTreeUtil.getParentOfType(deferStatement, GrAsyncExpression.class) == null) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("defer.statement.not.allowed.outside.async.expression"))
        .range(deferStatement.getDeferKeyword())
        .create();
    }
  }

  @Override
  public void visitYieldReturnStatement(@NotNull GrYieldReturnStatement yieldReturnStatement) {
    if (PsiTreeUtil.getParentOfType(yieldReturnStatement, GrAsyncExpression.class) == null) {
      PsiElement yieldKeyword = yieldReturnStatement.getYieldKeyword();
      PsiElement returnKeyword = yieldReturnStatement.getReturnKeyword();
      TextRange range = new TextRange(yieldKeyword.getTextRange().getStartOffset(), returnKeyword.getTextRange().getEndOffset());
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("yield.return.statement.not.allowed.outside.async.expression"))
        .range(range)
        .create();
    }
  }

  @Override
  public void visitAsyncExpression(@NotNull GrAsyncExpression asyncExpression) {
    GrFunctionalExpression body = asyncExpression.getBody();
    if (body == null) return;
    GrParameterList parameterList = body.getParameterList();
    if (body.getAllParameters().length > 1) {
      String message = GroovyBundle.message("async.closure.lambda.has.too.many.parameters", body instanceof GrLambdaExpression ? 2 : 1);
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(parameterList).create();
    }
  }
}

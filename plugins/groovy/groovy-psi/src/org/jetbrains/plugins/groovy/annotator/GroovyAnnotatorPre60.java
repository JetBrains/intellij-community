// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrDeferStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLoopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrYieldReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAsyncExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAwaitExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

/**
 * @author Bas Leijdekkers
 */
final class GroovyAnnotatorPre60 extends GroovyElementVisitor {
  private final @NotNull AnnotationHolder myHolder;

  GroovyAnnotatorPre60(@NotNull AnnotationHolder holder) {
    myHolder = holder;
  }

  @Override
  public void visitModifierList(@NotNull GrModifierList modifierList) {
    PsiElement modifier = modifierList.getModifier(GrModifier.VAL);
    if (modifier != null) {
      PsiElement parent = modifierList.getParent();
      if (!(parent instanceof GrMethod) && (!(parent instanceof GrVariableDeclaration d) || !d.isTuple())) {
        myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("unsupported.val.declaration"))
          .range(modifier)
          .withFix(QuickfixUtil.fixToIntention(modifier, new GrRemoveModifierFix(GrModifier.VAL)))
          .create();
      }
    }
  }

  @Override
  public void visitAnnotation(@NotNull GrAnnotation annotation) {
    if (annotation.getOwner() instanceof GrLoopStatement) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("unsupported.annotation.on.loop")).range(annotation).create();
    }
  }

  @Override
  public void visitImportStatement(@NotNull GrImportStatement importStatement) {
    if (importStatement.isModule()) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("unsupported.module.import"))
        .range(importStatement)
        .create();
    }
  }

  @Override
  public void visitForStatement(@NotNull GrForStatement forStatement) {
    ASTNode node = forStatement.getNode().findChildByType(GroovyTokenTypes.kAWAIT);
    if (node == null) return;
    myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("unsupported.async.await")).range(node.getPsi()).create();
  }

  @Override
  public void visitDeferStatement(@NotNull GrDeferStatement deferStatement) {
    myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("unsupported.async.await"))
      .range(deferStatement.getDeferKeyword())
      .create();
  }

  @Override
  public void visitAsyncExpression(@NotNull GrAsyncExpression asyncExpression) {
    myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("unsupported.async.await"))
      .range(asyncExpression.getAsyncKeyword())
      .create();  }

  @Override
  public void visitAwaitExpression(@NotNull GrAwaitExpression awaitExpression) {
    myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("unsupported.async.await"))
      .range(awaitExpression.getAwaitKeyword())
      .create();
  }

  @Override
  public void visitYieldReturnStatement(@NotNull GrYieldReturnStatement yieldReturnStatement) {
    PsiElement yieldKeyword = yieldReturnStatement.getYieldKeyword();
    PsiElement returnKeyword = yieldReturnStatement.getReturnKeyword();
    TextRange range = new TextRange(yieldKeyword.getTextRange().getStartOffset(), returnKeyword.getTextRange().getEndOffset());
    myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("unsupported.async.await")).range(range).create();
  }
}

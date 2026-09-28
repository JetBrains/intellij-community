// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
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
    super.visitModifierList(modifierList);
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
  public void visitImportStatement(@NotNull GrImportStatement importStatement) {
    super.visitImportStatement(importStatement);
    if (importStatement.isModule()) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("unsupported.module.import"))
        .range(importStatement)
        .create();
    }
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.inspections;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

public class SecondUnsafeCallQuickFix extends PsiUpdateModCommandQuickFix {

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("second.unsafe.call");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof GrReferenceExpression)) return;

    final PsiElement newDot = GroovyPsiElementFactory.getInstance(project).createDotToken(GroovyTokenTypes.mOPTIONAL_DOT.toString());
    ((GrReferenceExpression) element).replaceDotToken(newDot);
  }
}

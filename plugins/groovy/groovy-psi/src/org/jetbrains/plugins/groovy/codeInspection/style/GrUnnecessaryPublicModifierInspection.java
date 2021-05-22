// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;

public class GrUnnecessaryPublicModifierInspection extends LocalInspectionTool implements CleanupLocalInspectionTool {

  private static final LocalQuickFix FIX = new GrRemoveModifierFix(PsiModifier.PUBLIC);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement modifier) {
        if (modifier.getNode().getElementType() != GroovyTokenTypes.kPUBLIC) return;

        PsiElement list = modifier.getParent();
        if (!(list instanceof GrModifierList)) return;

        PsiElement parent = list.getParent();
        // Do not mark public on fields as unnecessary
        // It may be put there explicitly to prevent getter/setter generation.
        if (parent instanceof GrVariableDeclaration) return;

        holder.registerProblem(
          modifier,
          GroovyBundle.message("unnecessary.modifier.description", PsiModifier.PUBLIC),
          ProblemHighlightType.LIKE_UNUSED_SYMBOL,
          FIX
        );
      }
    };
  }
}

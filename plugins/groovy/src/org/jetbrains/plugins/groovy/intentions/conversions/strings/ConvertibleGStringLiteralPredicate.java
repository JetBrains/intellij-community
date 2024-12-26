// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions.strings;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;


class ConvertibleGStringLiteralPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(@NotNull PsiElement element) {
    if (!(element instanceof GrLiteral)) return false;
    if (ErrorUtil.containsError(element)) return false;

    final @NonNls String text = element.getText();

    if (text.charAt(0) != '"') return false;
    for (PsiElement child : element.getChildren()) {
      if (child instanceof GrStringInjection) {
        GrClosableBlock block = ((GrStringInjection)child).getClosableBlock();
        if (block != null && !checkClosure(block)) return false;
      }
    }
    return true;
  }

  private static boolean checkClosure(GrClosableBlock block) {
    if (block.hasParametersSection()) return false;
    final GrStatement[] statements = block.getStatements();
    if (statements.length != 1) return false;
    return statements[0] instanceof GrExpression || statements[0] instanceof GrReturnStatement;
  }
}

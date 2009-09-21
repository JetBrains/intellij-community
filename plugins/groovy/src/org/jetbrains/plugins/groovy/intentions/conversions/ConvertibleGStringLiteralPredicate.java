package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;


class ConvertibleGStringLiteralPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrLiteral)) return false;
    if (ErrorUtil.containsError(element)) return false;

    @NonNls final String text = element.getText();

    if (text.charAt(0) != '"') return false;
    for (PsiElement child : element.getChildren()) {
      if (child instanceof GrClosableBlock) {
        if (!checkClosure((GrClosableBlock)child)) return false;
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

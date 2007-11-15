package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;

class MergeElseIfPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrIfStatement)) {
      return false;
    }
    final GrIfStatement ifStatement = (GrIfStatement) element;
    final GrStatement thenBranch = ifStatement.getThenBranch();
    if (thenBranch == null) {
      return false;
    }
    final GrStatement elseBranch = ifStatement.getElseBranch();
    if (!(elseBranch instanceof GrBlockStatement)) {
      return false;
    }
    final GrOpenBlock block = ((GrBlockStatement) elseBranch).getBlock();
    final GrStatement[] statements = block.getStatements();
    return statements.length == 1 &&
        statements[0] instanceof GrIfStatement;
  }
}

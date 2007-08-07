package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrBlockStatement;

class MergeElseIfPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrIfStatement)) {
      return false;
    }
    final GrIfStatement ifStatement = (GrIfStatement) element;
    final GrCondition thenBranch = ifStatement.getThenBranch();
    if (!(thenBranch instanceof GrStatement)) {
      return false;
    }
    final GrCondition elseBranch = ifStatement.getElseBranch();
    if (!(elseBranch instanceof GrOpenBlock)) {
      return false;
    }
    final GrOpenBlock block = ((GrOpenBlock) elseBranch);
    final GrStatement[] statements = block.getStatements();
    return statements.length == 1 &&
        statements[0] instanceof GrIfStatement;
  }
}

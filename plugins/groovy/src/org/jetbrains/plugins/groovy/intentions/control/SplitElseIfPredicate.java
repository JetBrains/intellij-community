package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

class SplitElseIfPredicate implements PsiElementPredicate {

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
    return elseBranch instanceof GrIfStatement;
  }
}

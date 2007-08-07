package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.intentions.utils.ConditionalUtils;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;


class MergeIfAndPredicate implements PsiElementPredicate {
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrIfStatement)) {
      return false;
    }
    final GrIfStatement ifStatement = (GrIfStatement) element;
    if (ErrorUtil.containsError(ifStatement)) {
      return false;
    }
    GrCondition thenBranch = ifStatement.getThenBranch();
    if (!(thenBranch instanceof GrStatement)) {
      return false;
    }
    thenBranch = ConditionalUtils.stripBraces((GrStatement) thenBranch);
    if (!(thenBranch instanceof GrIfStatement)) {
      return false;
    }
    GrCondition elseBranch = ifStatement.getElseBranch();
    if (elseBranch != null) {
      elseBranch = ConditionalUtils.stripBraces((GrStatement) elseBranch);
      if (elseBranch != null) {
        return false;
      }
    }

    final GrIfStatement childIfStatement = (GrIfStatement) thenBranch;

    return childIfStatement.getElseBranch() == null;
  }
}

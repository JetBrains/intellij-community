package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;

class ForToEachPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrForStatement)) {
      return false;
    }
    final GrForStatement statement = (GrForStatement) element;
    final GrForClause clause = statement.getClause();
    if (!(clause instanceof GrForInClause)) {
      return false;
    }
    return !ErrorUtil.containsError(element);
  }
}

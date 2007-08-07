package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrBlockStatement;

public class SplitElseIfIntention extends Intention {

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new SplitElseIfPredicate();
  }

  public void processIntention(PsiElement element)
      throws IncorrectOperationException {
    final GrIfStatement parentStatement = (GrIfStatement) element;
    assert parentStatement != null;
    final GrCondition elseBranch = parentStatement.getElseBranch();
    replaceStatement("if(" + parentStatement.getCondition().getText()+ ")"+ parentStatement.getThenBranch().getText() +
        "else{\n" + elseBranch.getText() +"\n}", parentStatement);
  }
}

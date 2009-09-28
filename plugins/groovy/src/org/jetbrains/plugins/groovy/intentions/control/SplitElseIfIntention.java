package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

public class SplitElseIfIntention extends Intention {

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new SplitElseIfPredicate();
  }

  public void processIntention(@NotNull PsiElement element)
      throws IncorrectOperationException {
    final GrIfStatement parentStatement = (GrIfStatement) element;
    assert parentStatement != null;
    final GrStatement elseBranch = parentStatement.getElseBranch();
    IntentionUtils.replaceStatement("if(" + parentStatement.getCondition().getText()+ ")"+ parentStatement.getThenBranch().getText() +
        "else{\n" + elseBranch.getText() +"\n}", parentStatement);
  }
}

package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;

public class MergeElseIfIntention extends Intention {

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new MergeElseIfPredicate();
  }

  public void processIntention(@NotNull PsiElement element)
      throws IncorrectOperationException {
    final GrIfStatement parentStatement = (GrIfStatement) element;
    GrBlockStatement elseBlockStatement = (GrBlockStatement) parentStatement.getElseBranch();
    assert elseBlockStatement != null;
    final GrOpenBlock elseBranch = elseBlockStatement.getBlock();
    final GrStatement elseBranchContents = elseBranch.getStatements()[0];
    IntentionUtils.replaceStatement("if(" + parentStatement.getCondition().getText()+ ")"+ parentStatement.getThenBranch().getText() + "else " + elseBranchContents.getText(), parentStatement);
  }
}

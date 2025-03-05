// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author Max Medvedev
 */
public final class ReplaceIfWithTernaryIntention extends GrPsiUpdateIntention {
  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrIfStatement ifStatement = (GrIfStatement)element.getParent();

    final PsiElement thenBranch = skipBlock(ifStatement.getThenBranch());
    final PsiElement elseBranch = skipBlock(ifStatement.getElseBranch());

    Project project = context.project();
    if (thenBranch instanceof GrAssignmentExpression thenAssign && elseBranch instanceof GrAssignmentExpression elseAssign) {
      final GrAssignmentExpression assignment = (GrAssignmentExpression)GroovyPsiElementFactory.getInstance(
        project).createStatementFromText("a = b ? c : d");

      assignment.getLValue().replaceWithExpression(thenAssign.getLValue(), true);

      final GrConditionalExpression conditional = (GrConditionalExpression)assignment.getRValue();
      replaceConditional(conditional, ifStatement.getCondition(), thenAssign.getRValue(), elseAssign.getRValue());
      ifStatement.replaceWithStatement(assignment);
    }


    if (thenBranch instanceof GrReturnStatement thenReturn && elseBranch instanceof GrReturnStatement elseReturn) {
      final GrReturnStatement returnSt = (GrReturnStatement)GroovyPsiElementFactory.getInstance(project).createStatementFromText("return a ? b : c");
      final GrConditionalExpression conditional = (GrConditionalExpression)returnSt.getReturnValue();
      replaceConditional(conditional, ifStatement.getCondition(), thenReturn.getReturnValue(), elseReturn.getReturnValue());

      ifStatement.replaceWithStatement(returnSt);
    }
  }

  @SuppressWarnings("ConstantConditions")
  private static void replaceConditional(GrConditionalExpression conditional,
                                         GrExpression condition,
                                         GrExpression then,
                                         GrExpression elze) {
    conditional.getCondition().replaceWithExpression(condition, true);
    conditional.getThenBranch().replaceWithExpression(then, true);
    conditional.getElseBranch().replaceWithExpression(elze, true);
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return e -> {
      if (!e.getNode().getElementType().equals(GroovyTokenTypes.kIF)) return false;
      if (!(e.getParent() instanceof GrIfStatement ifStatement)) return false;

      final PsiElement thenBranch = skipBlock(ifStatement.getThenBranch());
      final PsiElement elseBranch = skipBlock(ifStatement.getElseBranch());

      if (thenBranch instanceof GrAssignmentExpression thenAssign && thenAssign.getRValue() != null &&
          elseBranch instanceof GrAssignmentExpression elseAssign && elseAssign.getRValue() != null) {
        final GrExpression lvalue1 = thenAssign.getLValue();
        final GrExpression lvalue2 = elseAssign.getLValue();
        return EquivalenceChecker.expressionsAreEquivalent(lvalue1, lvalue2);
      }

      if (thenBranch instanceof GrReturnStatement thenReturn && thenReturn.getReturnValue() != null &&
          elseBranch instanceof GrReturnStatement elseReturn && elseReturn.getReturnValue() != null) {
        return true;
      }

      return false;
    };
  }

  private static PsiElement skipBlock(PsiElement e) {
    if (e instanceof GrBlockStatement block && block.getBlock().getStatements().length == 1) {
      return block.getBlock().getStatements()[0];
    }
    else {
      return e;
    }
  }
}

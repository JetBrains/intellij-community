// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

/**
 * @author Max Medvedev
 */
public class FlipIfIntention extends Intention {
  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    final GrIfStatement ifStatement = (GrIfStatement)element.getParent();
    final GrIfStatement elseIf = getElseIf(ifStatement);
    final GrIfStatement elseIfCopy = (GrIfStatement)elseIf.copy();

    elseIf.getCondition().replaceWithExpression(ifStatement.getCondition(), true);
    elseIf.getThenBranch().replaceWithStatement(ifStatement.getThenBranch());

    ifStatement.getCondition().replaceWithExpression(elseIfCopy.getCondition(), true);
    ifStatement.getThenBranch().replaceWithStatement(elseIfCopy.getThenBranch());
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        if (!element.getNode().getElementType().equals(GroovyTokenTypes.kIF)) return false;

        final PsiElement parent = element.getParent();
        if (!(parent instanceof GrIfStatement)) return false;
        final GrIfStatement ifStatement = (GrIfStatement)parent;

        final GrIfStatement elseIf = getElseIf(ifStatement);
        return elseIf != null && checkIf(ifStatement) && checkIf(elseIf);
      }
    };
  }

  private static GrIfStatement getElseIf(GrIfStatement ifStatement) {
    final GrStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch == null) return null;

    if (elseBranch instanceof GrIfStatement) {
      return (GrIfStatement)elseBranch;
    }
    else {
      return null;
    }
  }

  private static boolean checkIf(GrIfStatement ifStatement) {
    return ifStatement.getCondition() != null && ifStatement.getThenBranch() != null;
  }
}

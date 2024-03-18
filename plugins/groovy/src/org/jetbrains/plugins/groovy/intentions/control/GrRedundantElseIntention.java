// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.IfEndInstruction;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.Objects;

/**
 * @author Max Medvedev
 */
public class GrRedundantElseIntention extends GrPsiUpdateIntention {
  public static final String HINT = "Remove redundant 'else' keyword";

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof GrIfStatement)) return;

    final GrIfStatement ifStatement = GroovyRefactoringUtil.addBlockIntoParent((GrIfStatement)parent);
    assert ifStatement.getParent() instanceof GrStatementOwner;
    final PsiElement statementOwner = ifStatement.getParent();

    final GrStatement branch = ifStatement.getElseBranch();
    if (branch == null) return;

    if (branch instanceof GrBlockStatement) {
      final GrOpenBlock block = ((GrBlockStatement)branch).getBlock();

      final PsiElement first = inferFirst(block.getLBrace());
      final PsiElement last = inferLast(block.getRBrace());
      if (!Objects.equals(first, block.getRBrace()) && !Objects.equals(last, block.getLBrace())) {
        // else block is not empty
        statementOwner.addRangeAfter(first, last, ifStatement);
      }
    }
    else {
      statementOwner.addAfter(branch, ifStatement);
    }
    branch.delete();

    updater.moveCaretTo(ifStatement.getTextRange().getEndOffset());
  }

  private static PsiElement inferFirst(PsiElement lbrace) {
    return PsiUtil.skipWhitespaces(lbrace.getNextSibling(), true);
  }

  private static PsiElement inferLast(PsiElement rbrace) {
    return PsiUtil.skipWhitespaces(rbrace.getPrevSibling(), false);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        if (!(element.getNode().getElementType() == GroovyTokenTypes.kELSE)) return false;

        final PsiElement parent = element.getParent();
        if (!(parent instanceof GrIfStatement ifStatement)) return false;

        final GrStatement branch = ifStatement.getThenBranch();
        final GrControlFlowOwner flowOwner = ControlFlowUtils.findControlFlowOwner(ifStatement);
        if (flowOwner == null) return false;

        final Instruction[] flow = flowOwner.getControlFlow();
        for (Instruction instruction : flow) {
          if (instruction instanceof IfEndInstruction && instruction.getElement() == ifStatement) {
            for (Instruction pred : instruction.allPredecessors()) {
              final PsiElement predElement = pred.getElement();
              if (predElement != null && PsiTreeUtil.isAncestor(branch, predElement, false))  {
                return false;
              }
            }
          }
        }

        return true;
      }
    };
  }
}

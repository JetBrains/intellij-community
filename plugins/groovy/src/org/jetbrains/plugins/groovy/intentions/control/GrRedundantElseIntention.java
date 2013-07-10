/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
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

/**
 * @author Max Medvedev
 */
public class GrRedundantElseIntention extends Intention {
  public static final String HINT = "Remove redundant 'else' keyword";
  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof GrIfStatement)) return;

    final GrIfStatement ifStatement = GroovyRefactoringUtil.addBlockIntoParent((GrIfStatement)parent);
    assert ifStatement.getParent() instanceof GrStatementOwner;
    final PsiElement statementOwner = ifStatement.getParent();

    final GrStatement branch = ifStatement.getElseBranch();
    if (branch == null) return;

    final PsiElement pos;
    if (branch instanceof GrBlockStatement) {
      final GrOpenBlock block = ((GrBlockStatement)branch).getBlock();

      final PsiElement first = inferFirst(block.getLBrace());
      final PsiElement last = inferLast(block.getRBrace());
      pos = statementOwner.addRangeAfter(first, last, ifStatement);
    }
    else {
      pos = statementOwner.addAfter(branch, ifStatement);
    }
    branch.delete();

    editor.getCaretModel().moveToOffset(pos.getTextRange().getStartOffset());
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
      public boolean satisfiedBy(PsiElement element) {
        if (!(element.getNode().getElementType() == GroovyTokenTypes.kELSE)) return false;

        final PsiElement parent = element.getParent();
        if (!(parent instanceof GrIfStatement)) return false;

        final GrIfStatement ifStatement = (GrIfStatement)parent;

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

/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RedundantElseInspection extends BaseInspection {

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "ConfusingElseBranch";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("redundant.else.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("redundant.else.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantElseVisitor();
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RemoveRedundantElseFix();
  }

  private static class RemoveRedundantElseFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement ifKeyword = descriptor.getPsiElement();
      final PsiIfStatement ifStatement = (PsiIfStatement)ifKeyword.getParent();
      if (ifStatement == null) {
        return;
      }
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch == null) {
        return;
      }
      PsiElement anchor = ifStatement;
      PsiElement parent = anchor.getParent();
      while (parent instanceof PsiIfStatement) {
        anchor = parent;
        parent = anchor.getParent();
      }
      if (elseBranch instanceof PsiBlockStatement) {
        final PsiBlockStatement elseBlock = (PsiBlockStatement)elseBranch;
        final PsiCodeBlock block = elseBlock.getCodeBlock();
        final PsiElement[] children = block.getChildren();
        if (children.length > 2) {
          parent.addRangeAfter(children[1], children[children.length - 2], anchor);
        }
      }
      else {
        parent.addAfter(elseBranch, anchor);
      }
      elseBranch.delete();
    }
  }

  private static class RedundantElseVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiStatement thenBranch = statement.getThenBranch();
      if (thenBranch == null) {
        return;
      }
      final PsiStatement elseBranch = statement.getElseBranch();
      if (elseBranch == null) {
        return;
      }
      if (ControlFlowUtils.statementMayCompleteNormally(thenBranch)) {
        return;
      }
      final PsiElement elseToken = statement.getElseElement();
      if (elseToken == null) {
        return;
      }
      if (parentCompletesNormally(statement)) {
        return;
      }
      registerError(elseToken);
    }

    private static boolean parentCompletesNormally(PsiElement element) {
      PsiElement parent = element.getParent();
      while (parent instanceof PsiIfStatement) {
        final PsiIfStatement ifStatement = (PsiIfStatement)parent;
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        if (elseBranch != element) {
          return true;
        }
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        if (ControlFlowUtils.statementMayCompleteNormally(thenBranch)) {
          return true;
        }
        element = parent;
        parent = element.getParent();
      }
      return !(parent instanceof PsiCodeBlock);
    }
  }
}

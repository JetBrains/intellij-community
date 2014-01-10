/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.VariableSearchUtils;
import org.jetbrains.annotations.NotNull;

public class ConstantIfStatementInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "constant.if.statement.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "constant.if.statement.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConstantIfStatementVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    //if (PsiUtil.isInJspFile(location)) {
    //    return null;
    //}
    return new ConstantIfStatementFix();
  }

  private static class ConstantIfStatementFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "constant.conditional.expression.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement ifKeyword = descriptor.getPsiElement();
      final PsiIfStatement statement =
        (PsiIfStatement)ifKeyword.getParent();
      assert statement != null;
      final PsiStatement thenBranch = statement.getThenBranch();
      final PsiStatement elseBranch = statement.getElseBranch();
      final PsiExpression condition = statement.getCondition();
      if (BoolUtils.isFalse(condition)) {
        if (elseBranch != null) {
          replaceStatementWithUnwrapping(elseBranch, statement);
        }
        else {
          deleteElement(statement);
        }
      }
      else {
        replaceStatementWithUnwrapping(thenBranch, statement);
      }
    }

    private static void replaceStatementWithUnwrapping(
      PsiStatement branch, PsiIfStatement statement)
      throws IncorrectOperationException {
      if (branch instanceof PsiBlockStatement &&
          !(statement.getParent() instanceof PsiIfStatement)) {
        final PsiCodeBlock parentBlock =
          PsiTreeUtil.getParentOfType(branch, PsiCodeBlock.class);
        if (parentBlock == null) {
          final String elseText = branch.getText();
          PsiReplacementUtil.replaceStatement(statement, elseText);
          return;
        }
        final PsiCodeBlock block =
          ((PsiBlockStatement)branch).getCodeBlock();
        final boolean hasConflicts =
          VariableSearchUtils.containsConflictingDeclarations(
            block, parentBlock);
        if (hasConflicts) {
          final String elseText = branch.getText();
          PsiReplacementUtil.replaceStatement(statement, elseText);
        }
        else {
          final PsiElement containingElement = statement.getParent();
          final PsiStatement[] statements = block.getStatements();
          if (statements.length > 0) {
            assert containingElement != null;
            final PsiJavaToken lBrace = block.getLBrace();
            final PsiJavaToken rBrace = block.getRBrace();
            PsiElement added = null;
            if (lBrace != null && rBrace != null) {
              final PsiElement firstNonBrace = lBrace.getNextSibling();
              final PsiElement lastNonBrace = rBrace.getPrevSibling();
              if (firstNonBrace != null && lastNonBrace != null) {
                added = containingElement.addRangeBefore(firstNonBrace, lastNonBrace, statement);
              }
            }
            if (added == null) {
              added = containingElement.addRangeBefore(statements[0],
                                                       statements[statements.length - 1], statement);
            }
            final Project project = statement.getProject();
            final CodeStyleManager codeStyleManager =
              CodeStyleManager.getInstance(project);
            codeStyleManager.reformat(added);
          }
          statement.delete();
        }
      }
      else {
        final String elseText = branch.getText();
        PsiReplacementUtil.replaceStatement(statement, elseText);
      }
    }
  }

  private static class ConstantIfStatementVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      final PsiStatement thenBranch = statement.getThenBranch();
      if (thenBranch == null) {
        return;
      }
      if (BoolUtils.isTrue(condition) || BoolUtils.isFalse(condition)) {
        registerStatementError(statement);
      }
    }
  }
}
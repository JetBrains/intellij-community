/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.dataflow;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class ReuseOfLocalVariableInspection extends BaseInspection {
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new ReuseOfLocalVariableFix();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "reuse.of.local.variable.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReuseOfLocalVariableVisitor();
  }

  private static class ReuseOfLocalVariableFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("reuse.of.local.variable.split.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
      final PsiLocalVariable variable = (PsiLocalVariable)referenceExpression.resolve();
      if (variable == null) return;
      final PsiAssignmentExpression assignment = (PsiAssignmentExpression)PsiUtil.skipParenthesizedExprUp(referenceExpression.getParent());
      if (assignment == null) return;
      PsiExpressionStatement assignmentStatement = (PsiExpressionStatement)assignment.getParent();
      final PsiExpression lExpression = PsiUtil.skipParenthesizedExprDown(assignment.getLExpression());
      if (lExpression == null) return;
      final String originalVariableName = lExpression.getText();
      final PsiType type = variable.getType();
      final PsiExpression rhs = assignment.getRExpression();
      final PsiCodeBlock variableBlock = Objects.requireNonNull(PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class));
      List<String> names = new VariableNameGenerator(variableBlock, VariableKind.LOCAL_VARIABLE)
        .byExpression(rhs).byType(type).byName(originalVariableName).generateAll(true);
      final String newVariableName = names.get(0);
      final SearchScope scope = new LocalSearchScope(assignmentStatement.getParent());
      final Query<PsiReference> query = ReferencesSearch.search(variable, scope, false);
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = psiFacade.getElementFactory();
      for (PsiReference reference : query) {
        final PsiElement referenceElement = reference.getElement();
        final TextRange textRange = assignmentStatement.getTextRange();
        if (referenceElement.getTextOffset() <= textRange.getEndOffset()) {
          continue;
        }
        final PsiExpression newExpression = factory.createExpressionFromText(newVariableName, referenceElement);
        referenceElement.replace(newExpression);
      }
      CommentTracker commentTracker = new CommentTracker();
      final String rhsText = rhs == null ? "" : commentTracker.text(rhs);
      @NonNls final String newStatementText = type.getCanonicalText() + ' ' + newVariableName + " =  " + rhsText + ';';

      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)BlockUtils.addAfter(assignmentStatement, factory.createStatementFromText(newStatementText, assignmentStatement));
      assignmentStatement = PsiTreeUtil.getPrevSiblingOfType(declarationStatement, PsiExpressionStatement.class);
      commentTracker.deleteAndRestoreComments(Objects.requireNonNull(assignmentStatement));
      final PsiElement[] elements = declarationStatement.getDeclaredElements();
      final PsiLocalVariable newVariable = (PsiLocalVariable)elements[0];
      updater.rename(newVariable, names);
    }
  }

  private static class ReuseOfLocalVariableVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);
      final PsiElement assignmentParent = assignment.getParent();
      if (!(assignmentParent instanceof PsiExpressionStatement)) {
        return;
      }
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignment.getLExpression());
      if (!(lhs instanceof PsiReferenceExpression reference)) {
        return;
      }
      final PsiElement referent = reference.resolve();
      if (!(referent instanceof PsiLocalVariable)) {
        return;
      }
      final PsiVariable variable = (PsiVariable)referent;
      if (variable.getInitializer() == null) {
        return;
      }
      final IElementType tokenType = assignment.getOperationTokenType();
      if (!JavaTokenType.EQ.equals(tokenType)) {
        return;
      }
      final PsiExpression rhs = assignment.getRExpression();
      if (VariableAccessUtils.variableIsUsed(variable, rhs)) {
        return;
      }
      final PsiCodeBlock variableBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (variableBlock == null) {
        return;
      }
      if (hasParentOfTypeBeforeAncestor(assignment, variableBlock, PsiLoopStatement.class, PsiTryStatement.class)) {
        // this could be weakened, slightly, if it could be verified
        // that a variable is used in only one branch of a try statement
        return;
      }
      final PsiElement assignmentBlock = assignmentParent.getParent();
      if (assignmentBlock == null) {
        return;
      }
      if (variableBlock.equals(assignmentBlock)) {
        registerError(lhs);
        return;
      }
      if (assignmentBlock instanceof PsiCodeBlock block) {
        boolean before = true;
        for (PsiStatement statement : block.getStatements()) {
          if (statement.equals(assignmentParent)) before = false;
          if (before) continue;
          if (statement instanceof PsiBreakStatement) break;
          if (statement instanceof PsiReturnStatement || statement instanceof PsiThrowStatement) {
            registerError(lhs);
            return;
          }
        }
      }
      PsiElement child = assignmentBlock;
      PsiElement parent = child.getParent();
      outer:
      while (parent != null) {
        boolean before = true;
        if (child instanceof PsiSwitchLabeledRuleStatement ruleStatement) {
          parent = ruleStatement.getEnclosingSwitchBlock();
        }
        else if (child instanceof PsiBreakStatement breakStatement) {
          // in switch statement
          parent = breakStatement.findExitedStatement();
        }
        else if (parent instanceof PsiCodeBlock) {
          for (PsiStatement statement : ((PsiCodeBlock)parent).getStatements()) {
            if (statement.equals(child)) {
              before = false;
              continue;
            }
            if (before) continue;
            if (VariableAccessUtils.variableIsUsed(variable, statement)) return;
            if (statement instanceof PsiReturnStatement || statement instanceof PsiThrowStatement) break outer;
          }
        }
        if (parent == variableBlock || parent == null) break;
        child = parent;
        parent = child.getParent();
      }
      registerError(lhs);
    }

    private static <T extends PsiElement> boolean hasParentOfTypeBeforeAncestor(@NotNull PsiElement descendant, @NotNull PsiElement ancestor,
                                                                                @NotNull Class<? extends T> @NotNull ... classes) {
      PsiElement elementToTest = descendant.getParent();
      while (elementToTest != null) {
        if (elementToTest.equals(ancestor)) return false;
        if (PsiTreeUtil.instanceOf(elementToTest, classes)) return true;
        elementToTest = elementToTest.getParent();
      }
      return false;
    }
  }
}
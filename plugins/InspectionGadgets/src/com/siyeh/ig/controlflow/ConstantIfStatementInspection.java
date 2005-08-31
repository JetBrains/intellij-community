/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class ConstantIfStatementInspection extends StatementInspection {

  private final ConstantIfStatementFix fix = new ConstantIfStatementFix();

  public String getGroupDisplayName() {
    return GroupNames.CONTROL_FLOW_GROUP_NAME;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ConstantIfStatementVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class ConstantIfStatementFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement ifKeyword = descriptor.getPsiElement();
      final PsiIfStatement statement =
        (PsiIfStatement)ifKeyword.getParent();
      assert statement != null;
      final PsiStatement thenBranch = statement.getThenBranch();
      final PsiStatement elseBranch = statement.getElseBranch();
      final PsiExpression condition = statement.getCondition();
      if (isFalse(condition)) {
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

    private void replaceStatementWithUnwrapping(PsiStatement branch,
                                                PsiIfStatement statement)
      throws IncorrectOperationException {
      if (branch instanceof PsiBlockStatement) {
        final PsiCodeBlock parentBlock = PsiTreeUtil
          .getParentOfType(branch, PsiCodeBlock.class);
        if (parentBlock == null) {
          final String elseText = branch.getText();
          replaceStatement(statement, elseText);
          return;
        }
        final PsiCodeBlock block = ((PsiBlockStatement)branch)
          .getCodeBlock();
        final boolean hasConflicts = containsConflictingDeclarations(block,
                                                                     parentBlock);
        if (hasConflicts) {
          final String elseText = branch.getText();
          replaceStatement(statement, elseText);
        }
        else {
          final PsiElement containingElement = statement.getParent();
          final PsiElement[] children = block.getChildren();
          if (children.length > 2) {
            assert containingElement != null;
            final PsiElement added =
              containingElement.addRangeBefore(children[1],
                                               children[children .length - 2],
                                               statement);
            final PsiManager manager = statement.getManager();
            final Project project = manager.getProject();
            final CodeStyleManager codeStyleManager =
              CodeStyleManager.getInstance(project);
            codeStyleManager.reformat(added);
            statement.delete();
          }
        }
      }
      else {
        final String elseText = branch.getText();
        replaceStatement(statement, elseText);
      }
    }
  }

  private static class ConstantIfStatementVisitor
    extends BaseInspectionVisitor {
    public void visitIfStatement(PsiIfStatement statment) {
      super.visitIfStatement(statment);
      final PsiExpression condition = statment.getCondition();
      if (condition == null) {
        return;
      }
      final PsiStatement thenBranch = statment.getThenBranch();
      if (thenBranch == null) {
        return;
      }
      if (isTrue(condition) || isFalse(condition)) {
        registerStatementError(statment);
      }
    }
  }

  private static boolean containsConflictingDeclarations(
    PsiCodeBlock block,
    PsiCodeBlock parentBlock) {
    final PsiStatement[] statements = block.getStatements();

    final Set<PsiElement> declaredVars = new HashSet<PsiElement>();
    for (final PsiStatement statement : statements) {
      if (statement instanceof PsiDeclarationStatement) {
        final PsiDeclarationStatement declaration =
          (PsiDeclarationStatement)statement;
        final PsiElement[] vars = declaration.getDeclaredElements();
        for (PsiElement var : vars) {
          if (var instanceof PsiLocalVariable) {
            declaredVars.add(var);
          }
        }
      }
    }
    for (Object declaredVar : declaredVars) {
      final PsiLocalVariable variable =
        (PsiLocalVariable)declaredVar;
      final String variableName = variable.getName();
      if (conflictingDeclarationExists(variableName, parentBlock,
                                       block)) {
        return true;
      }
    }

    return false;
  }

  private static boolean conflictingDeclarationExists(String name,
                                                      PsiCodeBlock parentBlock,
                                                      PsiCodeBlock exceptBlock) {
    final ConflictingDeclarationVisitor visitor =
      new ConflictingDeclarationVisitor(name, exceptBlock);
    parentBlock.accept(visitor);
    return visitor.hasConflictingDeclaration();
  }

  private static class ConflictingDeclarationVisitor
    extends PsiRecursiveElementVisitor {
    private final String variableName;
    private final PsiCodeBlock exceptBlock;
    private boolean hasConflictingDeclaration = false;

    ConflictingDeclarationVisitor(String variableName,
                                  PsiCodeBlock exceptBlock) {
      super();
      this.variableName = variableName;
      this.exceptBlock = exceptBlock;
    }

    public void visitElement(@NotNull PsiElement element) {
      if (!hasConflictingDeclaration) {
        super.visitElement(element);
      }
    }

    public void visitCodeBlock(PsiCodeBlock block) {
      if (hasConflictingDeclaration) {
        return;
      }
      if (block.equals(exceptBlock)) {
        return;
      }
      super.visitCodeBlock(block);
    }

    public void visitVariable(@NotNull PsiVariable variable) {
      if (hasConflictingDeclaration) {
        return;
      }
      super.visitVariable(variable);
      if (variable.getName().equals(variableName)) {
        hasConflictingDeclaration = true;
      }
    }

    public boolean hasConflictingDeclaration() {
      return hasConflictingDeclaration;
    }
  }

  private static boolean isFalse(PsiExpression expression) {
    final String text = expression.getText();
    return PsiKeyword.FALSE.equals(text);
  }

  private static boolean isTrue(PsiExpression expression) {
    final String text = expression.getText();
    return PsiKeyword.TRUE.equals(text);
  }
}

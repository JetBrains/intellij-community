/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class SwitchStatementWithConfusingDeclarationInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "LocalVariableUsedAndDeclaredInDifferentSwitchBranches";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("switch.statement.with.confusing.declaration.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SwitchStatementWithConfusingDeclarationVisitor();
  }

  private static class SwitchStatementWithConfusingDeclarationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      checkSwitchBlock(statement);
    }

    @Override
    public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
      checkSwitchBlock(expression);
    }

    private void checkSwitchBlock(PsiSwitchBlock block) {
      final PsiCodeBlock body = block.getBody();
      if (body == null) {
        return;
      }
      final Set<PsiLocalVariable> variablesInPreviousBranches = new HashSet<>(5);
      final Set<PsiLocalVariable> variablesInCurrentBranch = new HashSet<>(5);
      final PsiStatement[] statements = body.getStatements();
      final LocalVariableAccessVisitor visitor = new LocalVariableAccessVisitor(variablesInPreviousBranches);
      for (final PsiStatement child : statements) {
        if (child instanceof PsiDeclarationStatement declarationStatement) {
          final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
          for (final PsiElement declaredElement : declaredElements) {
            if (declaredElement instanceof PsiLocalVariable localVariable) {
              variablesInCurrentBranch.add(localVariable);
            }
          }
        }
        else if (child instanceof PsiSwitchLabelStatementBase) {
          variablesInPreviousBranches.addAll(variablesInCurrentBranch);
          variablesInCurrentBranch.clear();
        }
        child.accept(visitor);
      }
    }

    private final class LocalVariableAccessVisitor extends JavaRecursiveElementWalkingVisitor {

      private final Set<PsiLocalVariable> myVariablesInPreviousBranches;

      private LocalVariableAccessVisitor(Set<PsiLocalVariable> variablesInPreviousBranches) {
        myVariablesInPreviousBranches = variablesInPreviousBranches;
      }

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);
        final PsiExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier != null) {
          return;
        }
        final PsiElement element = referenceExpression.resolve();
        if (!(element instanceof PsiLocalVariable accessedVariable)) {
          return;
        }
        if (myVariablesInPreviousBranches.contains(accessedVariable)) {
          myVariablesInPreviousBranches.remove(accessedVariable);
          registerVariableError(accessedVariable);
        }
      }
    }
  }
}
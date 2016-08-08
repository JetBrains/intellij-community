/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class MergeParallelIfsPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    final PsiJavaToken token = (PsiJavaToken)element;
    final PsiElement parent = token.getParent();
    if (!(parent instanceof PsiIfStatement)) {
      return false;
    }
    final PsiIfStatement ifStatement = (PsiIfStatement)parent;
    final PsiElement nextStatement =
      PsiTreeUtil.skipSiblingsForward(ifStatement,
                                      PsiWhiteSpace.class);
    if (!(nextStatement instanceof PsiIfStatement)) {
      return false;
    }
    final PsiIfStatement nextIfStatement = (PsiIfStatement)nextStatement;
    if (ErrorUtil.containsError(ifStatement)) {
      return false;
    }
    if (ErrorUtil.containsError(nextIfStatement)) {
      return false;
    }
    if (!ifStatementsCanBeMerged(ifStatement, nextIfStatement)) {
      return false;
    }
    final PsiExpression condition = ifStatement.getCondition();
    final Set<PsiVariable> variables =
      VariableAccessUtils.collectUsedVariables(condition);
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    if (VariableAccessUtils.isAnyVariableAssigned(variables, thenBranch)) {
      return false;
    }
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    return !VariableAccessUtils.isAnyVariableAssigned(variables,
                                                      elseBranch);
  }

  public static boolean ifStatementsCanBeMerged(PsiIfStatement statement1,
                                                PsiIfStatement statement2) {
    final PsiStatement thenBranch = statement1.getThenBranch();
    final PsiStatement elseBranch = statement1.getElseBranch();
    if (thenBranch == null) {
      return false;
    }
    final PsiExpression firstCondition = statement1.getCondition();
    final PsiExpression secondCondition = statement2.getCondition();
    if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(firstCondition,
                                                                                  secondCondition)) {
      return false;
    }
    final PsiStatement nextThenBranch = statement2.getThenBranch();
    if (!canBeMerged(thenBranch, nextThenBranch)) {
      return false;
    }
    final PsiStatement nextElseBranch = statement2.getElseBranch();
    return elseBranch == null || nextElseBranch == null ||
           canBeMerged(elseBranch, nextElseBranch);
  }

  private static boolean canBeMerged(PsiStatement statement1,
                                     PsiStatement statement2) {
    if (!ControlFlowUtils.statementMayCompleteNormally(statement1)) {
      return false;
    }
    final Set<String> statement1Declarations =
      calculateTopLevelDeclarations(statement1);
    if (containsConflictingDeclarations(statement1Declarations, statement2)) {
      return false;
    }
    final Set<String> statement2Declarations =
      calculateTopLevelDeclarations(statement2);
    return !containsConflictingDeclarations(statement2Declarations,
                                            statement1);
  }

  private static boolean containsConflictingDeclarations(
    Set<String> declarations, PsiElement context) {
    final DeclarationVisitor visitor = new DeclarationVisitor(declarations);
    context.accept(visitor);
    return visitor.hasConflict();
  }

  private static Set<String> calculateTopLevelDeclarations(
    PsiStatement statement) {
    final Set<String> out = new HashSet<>();
    if (statement instanceof PsiDeclarationStatement) {
      addDeclarations((PsiDeclarationStatement)statement, out);
    }
    else if (statement instanceof PsiBlockStatement) {
      final PsiBlockStatement blockStatement =
        (PsiBlockStatement)statement;
      final PsiCodeBlock block = blockStatement.getCodeBlock();
      final PsiStatement[] statements = block.getStatements();
      for (PsiStatement statement1 : statements) {
        if (statement1 instanceof PsiDeclarationStatement) {
          addDeclarations((PsiDeclarationStatement)statement1, out);
        }
      }
    }
    return out;
  }

  private static void addDeclarations(PsiDeclarationStatement statement,
                                      Collection<String> declaredVariables) {
    final PsiElement[] elements = statement.getDeclaredElements();
    for (final PsiElement element : elements) {
      if (element instanceof PsiVariable) {
        final PsiVariable variable = (PsiVariable)element;
        final String name = variable.getName();
        declaredVariables.add(name);
      }
    }
  }

  private static class DeclarationVisitor
    extends JavaRecursiveElementWalkingVisitor {

    private final Set<String> declarations;
    private boolean hasConflict = false;

    private DeclarationVisitor(Set<String> declarations) {
      super();
      this.declarations = new HashSet<>(declarations);
    }

    @Override
    public void visitVariable(PsiVariable variable) {
      super.visitVariable(variable);
      final String name = variable.getName();
      for (Object declaration : declarations) {
        final String testName = (String)declaration;
        if (testName.equals(name)) {
          hasConflict = true;
        }
      }
    }

    public boolean hasConflict() {
      return hasConflict;
    }
  }
}
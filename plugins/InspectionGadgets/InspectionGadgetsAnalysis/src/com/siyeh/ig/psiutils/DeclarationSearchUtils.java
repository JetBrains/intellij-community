/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DeclarationSearchUtils {

  private DeclarationSearchUtils() {}

  public static boolean variableNameResolvesToTarget(
    @NotNull String variableName, @NotNull PsiVariable target,
    @NotNull PsiElement context) {

    final Project project = context.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiResolveHelper resolveHelper = psiFacade.getResolveHelper();
    final PsiVariable variable =
      resolveHelper.resolveAccessibleReferencedVariable(
        variableName, context);
    return target.equals(variable);
  }

  public static boolean containsConflictingDeclarations(PsiCodeBlock block, PsiCodeBlock parentBlock) {
    final PsiStatement[] statements = block.getStatements();
    if (statements.length == 0) {
      return false;
    }
    final int endOffset = block.getTextRange().getEndOffset();
    final List<PsiCodeBlock> affectedBlocks =
      SyntaxTraverser.psiTraverser(parentBlock)
        .filter(PsiCodeBlock.class)
        .filter(cb -> cb.getTextRange().getEndOffset() > endOffset)
        .addAllTo(new SmartList<>());
    final SearchScope affectedScope = new LocalSearchScope(affectedBlocks.toArray(PsiElement.EMPTY_ARRAY));
    final Project project = block.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();
    for (final PsiStatement statement : statements) {
      if (!(statement instanceof PsiDeclarationStatement)) {
        continue;
      }
      final PsiDeclarationStatement declaration = (PsiDeclarationStatement)statement;
      final PsiElement[] variables = declaration.getDeclaredElements();
      for (PsiElement variable : variables) {
        if (!(variable instanceof PsiLocalVariable)) {
          continue;
        }
        final PsiLocalVariable localVariable = (PsiLocalVariable)variable;
        final String variableName = localVariable.getName();
        if (variableName == null) {
          continue;
        }
        for (PsiCodeBlock codeBlock : affectedBlocks) {
          final PsiVariable target = resolveHelper.resolveAccessibleReferencedVariable(variableName, codeBlock);
          if (target instanceof PsiLocalVariable ||
              target instanceof PsiField && ReferencesSearch.search(target, affectedScope).findFirst() != null) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static PsiExpression findDefinition(@NotNull PsiReferenceExpression referenceExpression,
                                             @Nullable PsiVariable variable) {
    if (variable == null) {
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable)) {
        return null;
      }
      variable = (PsiVariable)target;
    }
    final PsiCodeBlock block = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    if (block == null) {
      return null;
    }
    final PsiElement[] defs = DefUseUtil.getDefs(block, variable, referenceExpression);
    if (defs.length != 1) {
      return null;
    }
    final PsiElement def = defs[0];
    if (def instanceof PsiVariable) {
      final PsiVariable target = (PsiVariable)def;
      final PsiExpression initializer = target.getInitializer();
      return ParenthesesUtils.stripParentheses(initializer);
    }
    else if (def instanceof PsiReferenceExpression) {
      final PsiElement parent = def.getParent();
      if (!(parent instanceof PsiAssignmentExpression)) {
        return null;
      }
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      if (assignmentExpression.getOperationTokenType() != JavaTokenType.EQ) {
        return null;
      }
      return ParenthesesUtils.stripParentheses(assignmentExpression.getRExpression());
    }
    return null;
  }

  public static boolean isTooExpensiveToSearch(PsiNamedElement element, boolean zeroResult) {
    final String name = element.getName();
    if (name == null) {
      return true;
    }
    final ProgressManager progressManager = ProgressManager.getInstance();
    final PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(element.getProject());
    final SearchScope useScope = element.getUseScope();
    if (!(useScope instanceof GlobalSearchScope)) {
      return zeroResult;
    }
    final PsiSearchHelper.SearchCostResult cost =
      searchHelper.isCheapEnoughToSearch(name, (GlobalSearchScope)useScope, null, progressManager.getProgressIndicator());
    if (cost == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES) {
      return zeroResult;
    }
    return cost == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES;
  }
}
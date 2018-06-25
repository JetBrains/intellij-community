/*
 * Copyright 2009-2015 Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.controlFlow.LocalsOrMyInstanceFieldsControlFlowPolicy;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

public class FinalUtils {

  private FinalUtils() {}

  public static boolean canBeFinal(@NotNull PsiVariable variable) {
    if (variable.getInitializer() != null || variable instanceof PsiParameter) {
      // parameters have an implicit initializer
      return !VariableAccessUtils.variableIsAssigned(variable);
    }
    if (variable instanceof PsiField && !HighlightControlFlowUtil.isFieldInitializedAfterObjectConstruction((PsiField)variable)) {
      return false;
    }
    PsiElement scope = variable instanceof PsiField
                       ? PsiUtil.getTopLevelClass(variable)
                       : PsiUtil.getVariableCodeBlock(variable, null);
    if (scope == null) return false;
    Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems = new THashMap<>();
    Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems = new THashMap<>();
    PsiElementProcessor elementDoesNotViolateFinality = e -> {
      if (!(e instanceof PsiReferenceExpression)) return true;
      PsiReferenceExpression ref = (PsiReferenceExpression)e;
      if (!ref.isReferenceTo(variable)) return true;
      HighlightInfo highlightInfo = HighlightControlFlowUtil
        .checkVariableInitializedBeforeUsage(ref, variable, uninitializedVarProblems, variable.getContainingFile(), true);
      if (highlightInfo != null) return false;
      if (!PsiUtil.isAccessedForWriting(ref)) return true;
      if (!LocalsOrMyInstanceFieldsControlFlowPolicy.isLocalOrMyInstanceReference(ref)) return false;
      if (ControlFlowUtil.isVariableAssignedInLoop(ref, variable)) return false;
      if (variable instanceof PsiField) {
        if (PsiUtil.findEnclosingConstructorOrInitializer(ref) == null) return false;
        PsiElement innerClass = HighlightControlFlowUtil.getInnerClassVariableReferencedFrom(variable, ref);
        if (innerClass != null && innerClass != ((PsiField)variable).getContainingClass()) return false;
      }
      return HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo(variable, ref, finalVarProblems) == null;
    };
    return PsiTreeUtil.processElements(scope, elementDoesNotViolateFinality);
  }
}
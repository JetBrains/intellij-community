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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
* @author Bas Leijdekkers
*/
class DefiniteAssignment {

  @NotNull
  private final PsiVariable variable;
  private final Map<PsiElement, Boolean> definitelyAssignedBeforeBreak = new HashMap();
  private final Map<PsiElement, Boolean> definitelyUnassignedBeforeBreak = new HashMap();
  private final Map<PsiElement, Boolean> definitelyAssignedBeforeContinue = new HashMap();
  private final Map<PsiElement, Boolean> definitelyUnassignedBeforeContinue = new HashMap();
  private final Map<PsiElement, Boolean> definitelyAssignedBeforeReturn = new HashMap();
  private final Map<PsiElement, Boolean> definitelyUnassignedBeforeReturn = new HashMap();
  private boolean definitelyAssigned = false;
  private boolean definitelyUnassigned = true;

  public DefiniteAssignment(@NotNull PsiVariable variable) {
   this.variable = variable;
  }

  public void and(boolean definitelyAssigned, boolean definitelyUnassigned) {
    this.definitelyAssigned &= definitelyAssigned;
    this.definitelyUnassigned &= definitelyUnassigned;
  }

  public void assign(@NotNull PsiReferenceExpression expression, boolean definiteAssignment) {
    if (definiteAssignment) {
      definitelyAssigned = true;
    }
    else {
      valueAccess(expression);
    }
    definitelyUnassigned = false;
  }

  public void andDefiniteAssignmentBeforeBreak(PsiStatement statement) {
    definitelyAssigned &= removeValue(statement, definitelyAssignedBeforeBreak);
    definitelyUnassigned &= removeValue(statement, definitelyUnassignedBeforeBreak);
  }

  public void andDefiniteAssignmentBeforeContinue(PsiStatement statement) {
    definitelyAssigned &= removeValue(statement, definitelyAssignedBeforeContinue);
    definitelyUnassigned &= removeValue(statement, definitelyUnassignedBeforeContinue);
  }

  public void andDefiniteAssignmentBeforeReturn(PsiMethod method) {
    definitelyAssigned &= removeValue(method, definitelyAssignedBeforeReturn);
    definitelyUnassigned &= removeValue(method, definitelyUnassignedBeforeReturn);
  }

  private static boolean removeValue(PsiElement statement, Map<PsiElement, Boolean> map) {
    final Boolean aBoolean = map.remove(statement);
    return aBoolean == null || aBoolean.booleanValue();
  }

  @NotNull
  public final PsiVariable getVariable() {
    return variable;
  }

  public boolean isDefinitelyAssigned() {
    return definitelyAssigned;
  }

  public boolean isDefinitelyUnassigned() {
    return definitelyUnassigned;
  }

  public void set(boolean definitelyAssigned, boolean definitelyUnassigned) {
    this.definitelyAssigned = definitelyAssigned;
    this.definitelyUnassigned = definitelyUnassigned;
  }

  public boolean stop() {
    return false;
  }

  public void storeBeforeBreakStatement(PsiBreakStatement breakStatement) {
    final PsiStatement statement = breakStatement.findExitedStatement();
    if (statement == null) {
      return;
    }
    storeFor(statement, definitelyAssignedBeforeBreak, definitelyUnassignedBeforeBreak);
  }

  public void storeBeforeContinueStatement(PsiContinueStatement continueStatement) {
    final PsiStatement statement = continueStatement.findContinuedStatement();
    if (statement == null) {
      return;
    }
    storeFor(statement, definitelyAssignedBeforeContinue, definitelyUnassignedBeforeContinue);
  }

  public void storeBeforeReturn(PsiReturnStatement returnStatement) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(returnStatement, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
    if (method == null || !method.isConstructor()) {
      return;
    }
    storeFor(method, definitelyAssignedBeforeReturn, definitelyUnassignedBeforeReturn);
  }

  private void storeFor(PsiElement element,
                        Map<PsiElement, Boolean> definitelyAssignedMap,
                        Map<PsiElement, Boolean> definitelyUnassignedMap) {
    final Boolean existingDa = definitelyAssignedMap.get(element);
    final Boolean existingDu = definitelyUnassignedMap.get(element);
    definitelyAssignedMap.put(element, Boolean.valueOf(definitelyAssigned && (existingDa == null || existingDa.booleanValue())));
    definitelyUnassignedMap.put(element, Boolean.valueOf(definitelyUnassigned && (existingDu == null || existingDu.booleanValue())));
  }

  @Override @NonNls
  public String toString() {
    return "DefiniteAssignment{ variable=" + variable + ", definitelyAssigned=" + definitelyAssigned +
           ", definitelyUnassigned=" + definitelyUnassigned + '}';
  }

  public void valueAccess(PsiReferenceExpression expression) {}
}

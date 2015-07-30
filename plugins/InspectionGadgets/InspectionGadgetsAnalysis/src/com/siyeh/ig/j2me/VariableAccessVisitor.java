/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.j2me;

import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class VariableAccessVisitor extends JavaRecursiveElementWalkingVisitor {
  private final Map<PsiField, Integer> m_accessCounts =
    new HashMap<PsiField, Integer>(2);
  private final Set<PsiField> m_overAccessedFields =
    new HashSet<PsiField>(2);

  @Override
  public void visitReferenceExpression(
    @NotNull PsiReferenceExpression referenceExpression) {
    super.visitReferenceExpression(referenceExpression);
    final PsiExpression qualifier =
      referenceExpression.getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
      return;
    }
    final PsiElement element = referenceExpression.resolve();
    if (!(element instanceof PsiField)) {
      return;
    }
    final PsiField field = (PsiField)element;
    final Set<PsiField> overAccessedFields = m_overAccessedFields;
    if (overAccessedFields.contains(field)) {
      return;
    }
    if (ControlFlowUtils.isInLoop(referenceExpression)) {
      overAccessedFields.add(field);
    }
    final Map<PsiField, Integer> accessCounts = m_accessCounts;
    final Integer count = accessCounts.get(field);
    if (count == null) {
      accessCounts.put(field, 1);
    }
    else if (count.intValue() == 1) {
      accessCounts.put(field, 2);
    }
    else {
      overAccessedFields.add(field);
    }
  }

  Set<PsiField> getOveraccessedFields() {
    return Collections.unmodifiableSet(m_overAccessedFields);
  }
}

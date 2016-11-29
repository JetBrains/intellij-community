/*
 * Copyright 2007-2015 Bas Leijdekkers
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
package com.siyeh.ipp.opassign;

import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ReplaceOperatorAssignmentWithAssignmentIntention extends MutablyNamedIntention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new OperatorAssignmentPredicate();
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)element;
    final PsiJavaToken sign = assignmentExpression.getOperationSign();
    final String operator = sign.getText();
    return IntentionPowerPackBundle.message("replace.operator.assignment.with.assignment.intention.name", operator);
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    PsiReplacementUtil.replaceOperatorAssignmentWithAssignmentExpression((PsiAssignmentExpression)element);
  }
}
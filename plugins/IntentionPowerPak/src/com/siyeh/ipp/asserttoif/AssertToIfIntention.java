/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.asserttoif;

import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AssertToIfIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new AssertStatementPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiAssertStatement assertStatement = (PsiAssertStatement)element;
    @NonNls final StringBuilder newStatement = new StringBuilder();
    final PsiExpression condition = assertStatement.getAssertCondition();
    newStatement.append("if(").append(BoolUtils.getNegatedExpressionText(condition)).append(") throw new java.lang.AssertionError(");
    final PsiExpression description = assertStatement.getAssertDescription();
    if (description != null) {
      newStatement.append(description.getText());
    }
    newStatement.append(");");
    PsiReplacementUtil.replaceStatement(assertStatement, newStatement.toString());
  }
}
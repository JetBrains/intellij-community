/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public class ExpandBooleanIntention extends GrPsiUpdateIntention {


  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ExpandBooleanPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrStatement containingStatement = (GrStatement)element;
    if (ExpandBooleanPredicate.isBooleanAssignment(containingStatement)) {
      final GrAssignmentExpression assignmentExpression = (GrAssignmentExpression)containingStatement;
      final GrExpression rhs = assignmentExpression.getRValue();
      assert rhs != null;
      final String rhsText = rhs.getText();
      final GrExpression lhs = assignmentExpression.getLValue();
      final String lhsText = lhs.getText();
      @NonNls final String statement = "if(" + rhsText + "){\n" + lhsText + " = true\n}else{\n" + lhsText + " = false\n}";
      PsiImplUtil.replaceStatement(statement, containingStatement);
    }
    else if (ExpandBooleanPredicate.isBooleanReturn(containingStatement)) {
      final GrReturnStatement returnStatement = (GrReturnStatement)containingStatement;
      final GrExpression returnValue = returnStatement.getReturnValue();
      final String valueText = returnValue.getText();
      @NonNls final String statement = "if(" + valueText + "){\nreturn true\n}else{\nreturn false\n}";
      PsiImplUtil.replaceStatement(statement, containingStatement);
    }
  }
}

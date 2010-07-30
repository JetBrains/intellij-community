/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.intentions.utils.ConditionalUtils;
import org.jetbrains.plugins.groovy.intentions.utils.ParenthesesUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class MergeIfAndIntention extends Intention {


  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new MergeIfAndPredicate();
  }

  public void processIntention(@NotNull PsiElement element, Project project, Editor editor)
      throws IncorrectOperationException {
    final GrIfStatement parentStatement =
        (GrIfStatement) element;
    assert parentStatement != null;
    final GrStatement parentThenBranch = (GrStatement) parentStatement.getThenBranch();
    final GrIfStatement childStatement =
        (GrIfStatement) ConditionalUtils.stripBraces(parentThenBranch);

    final GrExpression childCondition = (GrExpression) childStatement.getCondition();
    final String childConditionText;
    if (ParenthesesUtils.getPrecendence(childCondition)
        > ParenthesesUtils.AND_PRECEDENCE) {
      childConditionText = '(' + childCondition.getText() + ')';
    } else {
      childConditionText = childCondition.getText();
    }

    final GrExpression parentCondition = (GrExpression) parentStatement.getCondition();
    final String parentConditionText;
    if (ParenthesesUtils.getPrecendence(parentCondition)
        > ParenthesesUtils.AND_PRECEDENCE) {
      parentConditionText = '(' + parentCondition.getText() + ')';
    } else {
      parentConditionText = parentCondition.getText();
    }

    final GrStatement childThenBranch = (GrStatement) childStatement.getThenBranch();
    @NonNls final String statement =
        "if(" + parentConditionText + "&&" + childConditionText + ')' +
            childThenBranch.getText();
    IntentionUtils.replaceStatement(statement, parentStatement);
  }
}

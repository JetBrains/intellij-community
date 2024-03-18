/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author Brice Dutheil
 * @author Hamlet D'Arcy
 */
public class SplitIfIntention extends GrPsiUpdateIntention {

  @Override
  protected void processIntention(@NotNull PsiElement andElement, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    GrBinaryExpression binaryExpression = (GrBinaryExpression) andElement.getParent();
    GrIfStatement ifStatement = (GrIfStatement) binaryExpression.getParent();

    GrExpression leftOperand = binaryExpression.getLeftOperand();
    GrExpression rightOperand = binaryExpression.getRightOperand();

    GrStatement thenBranch = ifStatement.getThenBranch();

    assert thenBranch != null;
    assert rightOperand != null;
    GrStatement newSplittedIfs = GroovyPsiElementFactory.getInstance(context.project())
      .createStatementFromText(
        "if(" + leftOperand.getText() +
           ") { \n" +
           "  if(" + rightOperand.getText() + ")" +
           thenBranch.getText() + "\n" +
           "}"
      );

    ifStatement.replaceWithStatement(newSplittedIfs);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return element -> element.getParent() instanceof GrBinaryExpression binOp &&
                      binOp.getRightOperand() != null &&
                      binOp.getParent() instanceof GrIfStatement ifStatement &&
                      ifStatement.getElseBranch() == null
                      && "&&".equals(element.getText());
  }
}

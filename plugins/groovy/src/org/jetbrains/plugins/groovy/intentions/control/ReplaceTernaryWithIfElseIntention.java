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
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author Andreas Arledal
 */
public class ReplaceTernaryWithIfElseIntention extends GrPsiUpdateIntention {

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    GrConditionalExpression parentTernary = findTernary(element);
    GroovyPsiElementFactory groovyPsiElementFactory = GroovyPsiElementFactory.getInstance(context.project());

    GrReturnStatement parentReturn = (GrReturnStatement)parentTernary.getParent();

    String condition = parentTernary.getCondition().getText();
    GrExpression thenBranch = parentTernary.getThenBranch();
    String thenText = thenBranch != null ? thenBranch.getText() : "";

    GrExpression elseBranch = parentTernary.getElseBranch();
    String elseText = elseBranch != null ? elseBranch.getText() : "";

    String text = "if (" + condition + ") { \nreturn " + thenText + "\n} else {\n return " + elseText + "\n}";
    GrIfStatement ifStatement = (GrIfStatement)groovyPsiElementFactory.createStatementFromText(text);
    ifStatement = parentReturn.replaceWithStatement(ifStatement);
    updater.moveCaretTo(ifStatement.getRParenth().getTextRange().getEndOffset());
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return element -> {
      GrConditionalExpression ternary = findTernary(element);
      return ternary != null &&
             ternary.getThenBranch() != null &&
             ternary.getElseBranch() != null &&
             ternary.getParent() instanceof GrReturnStatement;
    };
  }

  @Nullable
  private static GrConditionalExpression findTernary(PsiElement element) {
    GrConditionalExpression ternary = PsiTreeUtil.getParentOfType(element, GrConditionalExpression.class);
    if (ternary == null) {
      GrReturnStatement ret = PsiTreeUtil.getParentOfType(element, GrReturnStatement.class);
      if (ret != null && ret.getReturnValue() instanceof GrConditionalExpression value) {
        return value;
      }
    }
    return ternary;
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.Objects;

public final class ForToEachIntention extends GrPsiUpdateIntention {
  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new ForToEachPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrForStatement parentStatement =
        (GrForStatement) element;
    final GrForInClause clause = (GrForInClause) parentStatement.getClause();
    if (clause == null) return;
    final GrVariable valueVariable = Objects.requireNonNull(clause).getDeclaredVariable();
    if (valueVariable == null) return;
    final GrVariable indexVariable = clause.getIndexVariable();
    final GrStatement body = parentStatement.getBody();
    if (body == null) return;
    final String bodyText;
    if (body instanceof GrBlockStatement) {
      final String text = body.getText();
      bodyText = text.substring(1, text.length() - 1);
    } else {
      bodyText = body.getText();

    }

    GrExpression collection = clause.getIteratedExpression();
    assert collection != null;

    final @NonNls String statement = "x." + getMethodName(indexVariable) +
                                     "{" + valueVariable.getText() +
                                     getIndexVariableText(indexVariable) +  " -> " + bodyText + " }";
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(parentStatement.getProject());
    final GrMethodCallExpression eachExpression =
        (GrMethodCallExpression) factory.createTopElementFromText(statement);
    ((GrReferenceExpression) eachExpression.getInvokedExpression()).getQualifierExpression().replaceWithExpression(collection, true);
    parentStatement.replaceWithStatement(eachExpression);
  }

  @Override
  public @NotNull String getText(@NotNull PsiElement element) {
    final GrForStatement parentStatement =
      (GrForStatement) element;
    final GrForInClause clause = (GrForInClause) parentStatement.getClause();
    if (Objects.requireNonNull(clause).getIndexVariable() != null){
      return GroovyIntentionsBundle.message("for.to.each.with.index.intention.name");
    } else {
      return GroovyIntentionsBundle.message("for.to.each.intention.name");
    }
  }

  private static String getMethodName(@Nullable GrVariable indexVariable) {
    if(indexVariable == null) return "each";
    return "eachWithIndex";
  }

  private static String getIndexVariableText(@Nullable GrVariable indexVariable) {
    if(indexVariable == null) return "";
    return ", " + indexVariable.getText();
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public final class EachToForIntention extends GrPsiUpdateIntention {

  public static final @NonNls String OUTER = "Outer";
  public static final @NonNls String HINT = "Replace with for-in";

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new EachToForPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrMethodCallExpression expression = (GrMethodCallExpression)element;
    final GrClosableBlock block = expression.getClosureArguments()[0];
    final GrParameterList parameterList = block.getParameterList();
    final GrParameter[] parameters = parameterList.getParameters();

    String var;
    if (parameters.length == 1) {
      var = parameters[0].getText();
      var = StringUtil.replace(var, GrModifier.DEF, "");
    }
    else {
      var = "it";
    }

    final GrExpression invokedExpression = expression.getInvokedExpression();
    GrExpression qualifier = ((GrReferenceExpression)invokedExpression).getQualifierExpression();
    final GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(element.getProject());
    if (qualifier == null) {
      qualifier = elementFactory.createExpressionFromText("this");
    }

    StringBuilder builder = new StringBuilder();
    builder.append("for (").append(var).append(" in ").append(qualifier.getText()).append(") {\n");
    String text = block.getText();
    final PsiElement blockArrow = block.getArrow();
    int index;
    if (blockArrow != null) {
      index = blockArrow.getStartOffsetInParent() + blockArrow.getTextLength();
    }
    else {
      index = 1;
    }
    while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;
    text = text.substring(index, text.length() - 1);
    builder.append(text);
    builder.append("}");

    final GrStatement statement = elementFactory.createStatementFromText(builder.toString());
    GrForStatement forStatement = (GrForStatement)expression.replaceWithStatement(statement);
    final GrForClause clause = forStatement.getClause();
    if (!(clause instanceof GrForInClause)) return;
    final GrVariable variable = ((GrForInClause)clause).getDeclaredVariable();
    if (variable != null) {
      updater.rename(variable, List.of(variable.getName()));
    }

    updateReturnStatements(forStatement);
  }

  private static void updateReturnStatements(GrForStatement forStatement) {
    GrStatement body = forStatement.getBody();
    assert body != null;

    final Set<String> usedLabels = new HashSet<>();
    final Ref<Boolean> needLabel = Ref.create(false);

    body.accept(new GroovyRecursiveElementVisitor() {
      private int myLoops = 0;

      @Override
      public void visitReturnStatement(@NotNull GrReturnStatement returnStatement) {
        if (returnStatement.getReturnValue() != null) return;

        if (myLoops > 0) needLabel.set(true);
      }

      @Override
      public void visitLabeledStatement(@NotNull GrLabeledStatement labeledStatement) {
        super.visitLabeledStatement(labeledStatement);
        usedLabels.add(labeledStatement.getName());
      }

      @Override
      public void visitForStatement(@NotNull GrForStatement forStatement) {
        myLoops++;
        super.visitForStatement(forStatement);
        myLoops--;
      }

      @Override
      public void visitWhileStatement(@NotNull GrWhileStatement whileStatement) {
        myLoops++;
        super.visitWhileStatement(whileStatement);
        myLoops--;
      }

      @Override
      public void visitClosure(@NotNull GrClosableBlock closure) {
        //don't go into closures
      }

      @Override
      public void visitAnonymousClassDefinition(@NotNull GrAnonymousClassDefinition anonymousClassDefinition) {
        //don't go into anonymous
      }
    });

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(forStatement.getProject());

    final String continueText;
    if (needLabel.get()) {
      int i = 0;
      String label = OUTER;
      while (usedLabels.contains(label)) {
        label = OUTER + i;
        i++;
      }

      continueText = "continue "+ label;

      GrLabeledStatement labeled = (GrLabeledStatement)factory.createStatementFromText(label + ": while (true){}");

      labeled.getStatement().replaceWithStatement(forStatement);

      labeled = forStatement.replaceWithStatement(labeled);

      forStatement = (GrForStatement)labeled.getStatement();

      body = forStatement.getBody();
      assert body != null;
    }
    else {
      continueText = "continue";
    }

    final GrStatement continueStatement = factory.createStatementFromText(continueText);

    body.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReturnStatement(@NotNull GrReturnStatement returnStatement) {
        if (returnStatement.getReturnValue() == null) {
          returnStatement.replaceWithStatement(continueStatement);
        }
      }

      @Override
      public void visitClosure(@NotNull GrClosableBlock closure) {
        //don't go into closures
      }

      @Override
      public void visitAnonymousClassDefinition(@NotNull GrAnonymousClassDefinition anonymousClassDefinition) {
        //don't go into anonymous
      }
    });
  }

  private static class EachToForPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(@NotNull PsiElement element) {
      if (element instanceof GrMethodCallExpression expression) {
        //        final PsiElement parent = expression.getParent();
//        if (parent instanceof GrAssignmentExpression) return false;
//        if (parent instanceof GrArgumentList) return false;
//        if (parent instanceof GrReturnStatement) return false;
//        if (!(parent instanceof GrCodeBlock || parent instanceof GrIfStatement|| parent instanceof GrCaseSection)) return false;

        final GrExpression invokedExpression = expression.getInvokedExpression();
        if (invokedExpression instanceof GrReferenceExpression referenceExpression) {
          if ("each".equals(referenceExpression.getReferenceName())) {
            final GrArgumentList argumentList = expression.getArgumentList();
            if (PsiImplUtil.hasExpressionArguments(argumentList)) return false;
            if (PsiImplUtil.hasNamedArguments(argumentList)) return false;
            final GrClosableBlock[] closureArguments = expression.getClosureArguments();
            if (closureArguments.length != 1) return false;
            final GrParameter[] parameters = closureArguments[0].getParameterList().getParameters();
            if (parameters.length > 1) return false;
            return true;
          }
        }
      }
      return false;
    }
  }
}

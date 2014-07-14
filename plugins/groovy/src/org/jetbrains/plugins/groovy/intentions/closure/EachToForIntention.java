/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class EachToForIntention extends Intention {

  @NonNls public static final String OUTER = "Outer";
  @NonNls public static final String HINT = "Replace with For-In";

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new EachToForPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
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
    GrVariable variable = clause.getDeclaredVariable();

    forStatement = updateReturnStatements(forStatement);

    if (variable == null) return;

    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document doc = documentManager.getDocument(element.getContainingFile());
    if (doc == null) return;

    documentManager.doPostponedOperationsAndUnblockDocument(doc);
    editor.getCaretModel().moveToOffset(variable.getTextOffset());
    new VariableInplaceRenamer(variable, editor).performInplaceRename();
  }

  private static GrForStatement updateReturnStatements(GrForStatement forStatement) {
    GrStatement body = forStatement.getBody();
    assert body != null;

    final Set<String> usedLabels = ContainerUtil.newHashSet();
    final Ref<Boolean> needLabel = Ref.create(false);

    body.accept(new GroovyRecursiveElementVisitor() {
      private int myLoops = 0;

      @Override
      public void visitReturnStatement(GrReturnStatement returnStatement) {
        if (returnStatement.getReturnValue() != null) return;

        if (myLoops > 0) needLabel.set(true);
      }

      @Override
      public void visitLabeledStatement(GrLabeledStatement labeledStatement) {
        super.visitLabeledStatement(labeledStatement);
        usedLabels.add(labeledStatement.getName());
      }

      @Override
      public void visitForStatement(GrForStatement forStatement) {
        myLoops++;
        super.visitForStatement(forStatement);
        myLoops--;
      }

      @Override
      public void visitWhileStatement(GrWhileStatement whileStatement) {
        myLoops++;
        super.visitWhileStatement(whileStatement);
        myLoops--;
      }

      @Override
      public void visitClosure(GrClosableBlock closure) {
        //don't go into closures
      }

      @Override
      public void visitAnonymousClassDefinition(GrAnonymousClassDefinition anonymousClassDefinition) {
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
      public void visitReturnStatement(GrReturnStatement returnStatement) {
        if (returnStatement.getReturnValue() == null) {
          returnStatement.replaceWithStatement(continueStatement);
        }
      }

      @Override
      public void visitClosure(GrClosableBlock closure) {
        //don't go into closures
      }

      @Override
      public void visitAnonymousClassDefinition(GrAnonymousClassDefinition anonymousClassDefinition) {
        //don't go into anonymous
      }
    });

    return forStatement;
  }

  private static class EachToForPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element) {
      if (element instanceof GrMethodCallExpression) {
        final GrMethodCallExpression expression = (GrMethodCallExpression)element;
//        final PsiElement parent = expression.getParent();
//        if (parent instanceof GrAssignmentExpression) return false;
//        if (parent instanceof GrArgumentList) return false;
//        if (parent instanceof GrReturnStatement) return false;
//        if (!(parent instanceof GrCodeBlock || parent instanceof GrIfStatement|| parent instanceof GrCaseSection)) return false;

        final GrExpression invokedExpression = expression.getInvokedExpression();
        if (invokedExpression instanceof GrReferenceExpression) {
          GrReferenceExpression referenceExpression = (GrReferenceExpression)invokedExpression;
          if ("each".equals(referenceExpression.getReferenceName())) {
            final GrArgumentList argumentList = expression.getArgumentList();
            if (argumentList != null) {
              if (PsiImplUtil.hasExpressionArguments(argumentList)) return false;
              if (PsiImplUtil.hasNamedArguments(argumentList)) return false;
            }
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

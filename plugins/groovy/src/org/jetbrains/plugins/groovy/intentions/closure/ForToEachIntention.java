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
package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
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

public class ForToEachIntention extends Intention {


  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ForToEachPredicate();
  }

  public void processIntention(@NotNull PsiElement element, Project project, Editor editor)
      throws IncorrectOperationException {
    final GrForStatement parentStatement =
        (GrForStatement) element;
    final GrForInClause clause = (GrForInClause) parentStatement.getClause();
    final GrVariable var = clause.getDeclaredVariables()[0];
    final GrStatement body = parentStatement.getBody();
    final String bodyText;
    if (body instanceof GrBlockStatement) {
      final String text = body.getText();
      bodyText = text.substring(1, text.length() - 1);
    } else {
      bodyText = body.getText();

    }

    GrExpression collection = clause.getIteratedExpression();
    assert collection != null;
    @NonNls final String statement = "x.each{" + var.getText() + " -> " + bodyText + " }";
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(parentStatement.getProject());
    final GrMethodCallExpression eachExpression =
        (GrMethodCallExpression) factory.createTopElementFromText(statement);
    ((GrReferenceExpression) eachExpression.getInvokedExpression()).getQualifierExpression().replaceWithExpression(collection, true);
    parentStatement.replaceWithStatement(eachExpression);
  }
}

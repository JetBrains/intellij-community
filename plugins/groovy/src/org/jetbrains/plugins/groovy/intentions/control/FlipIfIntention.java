/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

/**
 * @author Max Medvedev
 */
public class FlipIfIntention extends Intention {
  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    final GrIfStatement ifStatement = DefaultGroovyMethods.asType(element.getParent(), GrIfStatement.class);
    final GrIfStatement elseIf = getElseIf(ifStatement);

    final GrIfStatement elseIfCopy = DefaultGroovyMethods.asType(elseIf.copy(), GrIfStatement.class);

    elseIf.getCondition().replaceWithExpression(ifStatement.getCondition(), true);
    elseIf.getThenBranch().replaceWithStatement(ifStatement.getThenBranch());

    ifStatement.getCondition().replaceWithExpression(elseIfCopy.getCondition(), true);
    ifStatement.getThenBranch().replaceWithStatement(elseIfCopy.getThenBranch());
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (!element.getNode().getElementType().equals(GroovyTokenTypes.kIF)) return false;
        if (!(element.getParent() instanceof GrIfStatement)) return false;

        final GrIfStatement ifStatement = DefaultGroovyMethods.asType(element.getParent(), GrIfStatement.class);

        final GrIfStatement elseIf = getElseIf(ifStatement);
        return elseIf != null && checkIf(ifStatement) && checkIf(elseIf);
      }
    };
  }

  private static GrIfStatement getElseIf(GrIfStatement ifStatement) {
    final GrStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch == null) return null;

    if (elseBranch instanceof GrIfStatement) {
      return DefaultGroovyMethods.asType(elseBranch, GrIfStatement.class);
    }
    else {
      return null;
    }
  }

  private static boolean checkIf(GrIfStatement ifStatement) {
    return ifStatement.getCondition() != null && ifStatement.getThenBranch() != null;
  }
}

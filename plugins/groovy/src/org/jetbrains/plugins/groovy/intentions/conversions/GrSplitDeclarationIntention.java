/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

/**
 * @author Max Medvedev
 */
public class GrSplitDeclarationIntention extends Intention {
  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    if (element instanceof GrVariableDeclaration) {
      GrVariable[] variables = ((GrVariableDeclaration)element).getVariables();
      if (variables.length == 1) {
        GrVariable var = variables[0];
        GrExpression initializer = var.getInitializerGroovy();
        if (initializer != null) {
          GrExpression assignment = GroovyPsiElementFactory.getInstance(project)
            .createExpressionFromText(var.getName() + " = " + initializer.getText());
          initializer.delete();
          element = GroovyRefactoringUtil.addBlockIntoParent(element);
          element.getParent().addAfter(assignment, element);
        }
      }
      else if (variables.length>1) {
        String modifiers = ((GrVariableDeclaration)element).getModifierList().getText();
        GrStatement[] sts = new GrStatement[variables.length];
        for (int i = 0; i < variables.length; i++) {
          sts[i] = GroovyPsiElementFactory.getInstance(project).createStatementFromText(modifiers + " " + variables[i].getText());
        }

        element = GroovyRefactoringUtil.addBlockIntoParent(element);

        for (int i = sts.length - 1; i >= 0; i--) {
          element.getParent().addAfter(sts[i], element);
        }
      }
    }
  }

  private String myText = "";

  @NotNull
  @Override
  public String getText() {
    return myText;
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (element instanceof GrVariableDeclaration) {
          GrVariable[] variables = ((GrVariableDeclaration)element).getVariables();
          if (variables.length > 1 && GroovyRefactoringUtil.isLocalVariable(variables[0])) {
            myText = GroovyIntentionsBundle.message("split.into.separate.declaration");
            return true;
          }
          else if (variables.length == 1 &&
                   GroovyRefactoringUtil.isLocalVariable(variables[0]) &&
                   variables[0].getInitializerGroovy() != null) {
            myText = GroovyIntentionsBundle.message("split.into.declaration.and.assignment");
            return true;
          }
        }
        return false;
      }
    };
  }
}

/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

public abstract class GroovyFix implements LocalQuickFix {
  public static final GroovyFix EMPTY_FIX = new GroovyFix() {
    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
    }

    @NotNull
    @Override
    public String getFamilyName() {
      throw new UnsupportedOperationException();
    }
  };
  public static final GroovyFix[] EMPTY_ARRAY = new GroovyFix[0];



  @Override
  public void applyFix(@NotNull Project project,
                       @NotNull ProblemDescriptor descriptor) {
    final PsiElement problemElement = descriptor.getPsiElement();
    if (problemElement == null || !problemElement.isValid()) {
      return;
    }
    try {
      doFix(project, descriptor);
    } catch (IncorrectOperationException e) {
      final Class<? extends GroovyFix> aClass = getClass();
      final String className = aClass.getName();
      final Logger logger = Logger.getInstance(className);
      logger.error(e);
    }
  }

  protected abstract void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
      throws IncorrectOperationException;


  protected static void replaceExpression(GrExpression expression, @NonNls String newExpression) {
    GrInspectionUtil.replaceExpression(expression, newExpression);
  }

  protected static void replaceStatement(GrStatement statement, @NonNls String newStatement) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(statement.getProject());
    final GrStatement newCall = (GrStatement)factory.createTopElementFromText(newStatement);
    statement.replaceWithStatement(newCall);
  }

  /**
   * unwraps surrounding blocks from newStatement.
   */
  protected static void replaceStatement(GrStatement oldStatement, GrStatement newStatement) throws IncorrectOperationException {
    if (newStatement instanceof GrBlockStatement blockStatement) {
      final GrOpenBlock openBlock = blockStatement.getBlock();
      final GrStatement[] statements = openBlock.getStatements();
      if (statements.length == 0) {
        oldStatement.removeStatement();
      }
      else {
        final PsiElement parent = oldStatement.getParent();
        if (parent instanceof GrStatementOwner statementOwner) {
          for (GrStatement statement : statements) {
            statementOwner.addStatementBefore(statement, oldStatement);
          }
          oldStatement.removeStatement();
        }
        else if (parent instanceof GrControlStatement) {
          oldStatement.replace(newStatement);
        }
      }
    }
    else {
      oldStatement.replaceWithStatement(newStatement);
    }
  }
}

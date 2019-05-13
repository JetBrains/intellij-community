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
package org.jetbrains.plugins.groovy.codeInspection.control;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GroovyConditionalWithIdenticalBranchesInspection extends BaseInspection {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return "Conditional expression with identical branches";
  }

  @Override
  public String buildErrorString(Object... args) {
    return "Conditional expression with identical branches #loc";
  }

  @Override
  public GroovyFix buildFix(@NotNull PsiElement location) {
    return new CollapseConditionalFix();
  }

  private static class CollapseConditionalFix extends GroovyFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return "Collapse conditional expression";
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof GrConditionalExpression)) return;
      final GrConditionalExpression expression = (GrConditionalExpression)element;
      final GrExpression thenBranch = expression.getThenBranch();
      replaceExpression(expression, thenBranch.getText());
    }
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(@NotNull GrConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final GrExpression thenBranch = expression.getThenBranch();
      final GrExpression elseBranch = expression.getElseBranch();
      if (thenBranch == null || elseBranch == null) {
        return;
      }
      if (EquivalenceChecker.expressionsAreEquivalent(thenBranch, elseBranch)) {
        registerStatementError(expression);
      }
    }
  }
}
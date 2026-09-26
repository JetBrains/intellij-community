/*
 * Copyright 2007-2008 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import static org.jetbrains.plugins.groovy.codeInspection.GroovyFix.replaceStatement;

public final class GroovyConstantIfStatementInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.ref.statement.can.be.simplified");
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new ConstantIfStatementVisitor();
  }

  @Override
  public LocalQuickFix buildFix(@NotNull PsiElement location) {
    return new ConstantIfStatementFix();
  }

  private static class ConstantIfStatementFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return GroovyBundle.message("intention.family.name.simplify");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final GrIfStatement ifStatement = (GrIfStatement) element.getParent();
      assert ifStatement != null;
      final GrStatement thenBranch = ifStatement.getThenBranch();
      final GrStatement elseBranch = ifStatement.getElseBranch();
      final GrExpression condition = ifStatement.getCondition();
      // todo still needs some handling for conflicting declarations
      if (isFalse(condition)) {
        if (elseBranch != null) {
          replaceStatement(ifStatement, (GrStatement)elseBranch.copy());
        } else {
          ifStatement.delete();
        }
      } else {
        replaceStatement(ifStatement, (GrStatement)thenBranch.copy());
      }
    }
  }

  private static class ConstantIfStatementVisitor
      extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull GrIfStatement statement) {
      super.visitIfStatement(statement);
      final GrExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      final GrStatement thenBranch = statement.getThenBranch();
      if (thenBranch == null) {
        return;
      }
      if (isTrue(condition) || isFalse(condition)) {
        registerStatementError(statement);
      }
    }
  }

  private static boolean isFalse(GrExpression expression) {
    final @NonNls String text = expression.getText();
    return "false".equals(text);
  }

  private static boolean isTrue(GrExpression expression) {
    final @NonNls String text = expression.getText();
    return "true".equals(text);
  }
}
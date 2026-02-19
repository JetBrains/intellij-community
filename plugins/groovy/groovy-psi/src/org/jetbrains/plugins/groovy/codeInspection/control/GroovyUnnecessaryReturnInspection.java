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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public final class GroovyUnnecessaryReturnInspection extends BaseInspection {

  @Override
  protected @Nullable String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.ref.is.unnecessary.as.last.statement.in.method.with.no.return.value");
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(@NotNull PsiElement location) {
    return new UnnecessaryReturnFix();
  }

  private static class UnnecessaryReturnFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return GroovyBundle.message("intention.family.name.remove.unnecessary.return");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement returnKeywordElement, @NotNull ModPsiUpdater updater) {
      final GrReturnStatement returnStatement = (GrReturnStatement) returnKeywordElement.getParent();
      assert returnStatement != null;
      returnStatement.removeStatement();
    }
  }

  private static class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitReturnStatement(@NotNull GrReturnStatement returnStatement) {
      super.visitReturnStatement(returnStatement);

      final GrExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) return;

      final GrMethod method = PsiTreeUtil.getParentOfType(returnStatement, GrMethod.class);
      if (method == null) return;

      final GrOpenBlock body = method.getBlock();
      if (body == null) return;

      if (ControlFlowUtils.openBlockCompletesWithStatement(body, returnStatement)) {
        registerStatementError(returnStatement);
      }
    }
  }
}
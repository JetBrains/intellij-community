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
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLoopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrContinueStatement;

public final class GroovyUnnecessaryContinueInspection extends BaseInspection {

  @Override
  protected @Nullable String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.ref.is.unnecessary.as.last.statement.in.loop");

  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(@NotNull PsiElement location) {
    return new UnnecessaryContinueFix();
  }

  private static class UnnecessaryContinueFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return GroovyBundle.message("intention.family.name.remove.unnecessary.continue");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement continueKeywordElement, @NotNull ModPsiUpdater updater) {
      final GrContinueStatement continueStatement = (GrContinueStatement) continueKeywordElement.getParent();
      assert continueStatement != null;
      continueStatement.removeStatement();
    }
  }

  private static class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitContinueStatement(@NotNull GrContinueStatement continueStatement) {
      super.visitContinueStatement(continueStatement);
      if (continueStatement.getContainingFile().getViewProvider() instanceof TemplateLanguageFileViewProvider) {
        return;
      }
      final GrStatement continuedStatement = continueStatement.findTargetStatement();
      if (continuedStatement == null) {
        return;
      }

      if (!(continuedStatement instanceof GrLoopStatement)) return;
      final GrStatement body = ((GrLoopStatement)continuedStatement).getBody();
      if (body == null) return;


      if (body instanceof GrBlockStatement && ControlFlowUtils.blockCompletesWithStatement((GrBlockStatement)body, continueStatement) ||
          !(body instanceof GrBlockStatement) && ControlFlowUtils.statementCompletesWithStatement(body, continueStatement)){
        registerStatementError(continueStatement);
      }
    }
  }
}
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
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLoopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrContinueStatement;

public class GroovyUnnecessaryContinueInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Unnecessary 'continue' statement";
  }

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return "#ref is unnecessary as the last statement in a loop #loc";

  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  @Override
  @Nullable
  protected GroovyFix buildFix(@NotNull PsiElement location) {
    return new UnnecessaryContinueFix();
  }

  private static class UnnecessaryContinueFix extends GroovyFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return "Remove unnecessary continue";
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final PsiElement continueKeywordElement = descriptor.getPsiElement();
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
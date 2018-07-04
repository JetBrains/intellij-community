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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public abstract class BaseInspectionVisitor extends GroovyElementVisitor {
  private BaseInspection inspection = null;
  private ProblemsHolder problemsHolder = null;
  private boolean onTheFly = false;

  void initialize(BaseInspection inspection, ProblemsHolder problemsHolder, boolean onTheFly) {
    this.inspection = inspection;
    this.problemsHolder = problemsHolder;
    this.onTheFly = onTheFly;
  }

  protected void registerStatementError(GrStatement statement, Object... args) {
    final PsiElement statementToken = statement.getFirstChild();
    registerError(statementToken, args);
  }

  protected void registerError(PsiElement location) {
    if (location == null) {
      return;
    }
    final LocalQuickFix[] fix = createFixes(location);
    String description = StringUtil.notNullize(inspection.buildErrorString(location));

    registerError(location, description, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  protected void registerMethodError(GrMethod method, Object... args) {
    if (method == null) {
      return;
    }
    final LocalQuickFix[] fixes = createFixes(method);
    String description = StringUtil.notNullize(inspection.buildErrorString(args));

    registerError(method.getNameIdentifierGroovy(), description, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  protected void registerVariableError(GrVariable variable, Object... args) {
    if (variable == null) {
      return;
    }
    final LocalQuickFix[] fix = createFixes(variable);
    final String description = StringUtil.notNullize(inspection.buildErrorString(args));
    registerError(variable.getNameIdentifierGroovy(), description, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  protected void registerMethodCallError(GrMethodCall method, Object... args) {
    if (method == null) {
      return;
    }
    final LocalQuickFix[] fixes = createFixes(method);
    final String description = StringUtil.notNullize(inspection.buildErrorString(args));

    final GrExpression invoked = method.getInvokedExpression();
    final PsiElement nameElement = ((GrReferenceExpression)invoked).getReferenceNameElement();
    assert nameElement != null;
    registerError(nameElement, description, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  protected void registerError(@NotNull PsiElement location,
                               @NotNull String description,
                               @Nullable LocalQuickFix[] fixes,
                               ProblemHighlightType highlightType) {
    problemsHolder.registerProblem(location, description, highlightType, fixes);
  }

  protected void registerError(@NotNull PsiElement location, Object... args) {
    registerError(location, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, args);
  }

  protected void registerError(@NotNull PsiElement location,
                               ProblemHighlightType highlightType,
                               Object... args) {
    final LocalQuickFix[] fix = createFixes(location);
    final String description = StringUtil.notNullize(inspection.buildErrorString(args));
    registerError(location, description, fix, highlightType);
  }

  @Nullable
  private LocalQuickFix[] createFixes(@NotNull PsiElement location) {
    if (!onTheFly &&
        inspection.buildQuickFixesOnlyForOnTheFlyErrors()) {
      return null;
    }
    final GroovyFix fix = inspection.buildFix(location);
    if (fix == null) {
      return null;
    }
    return new GroovyFix[]{fix};
  }

  public int getErrorCount() {
    return problemsHolder.getResultCount();
  }
}

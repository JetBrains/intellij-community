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
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import static org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle.message;

/**
 * @author Max Medvedev
 */
public class GrReassignedInClosureLocalVarInspection extends BaseInspection {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Local variable is reassigned in closure or anonymous class";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);

        if (!PsiUtil.isLValue(referenceExpression)) return;
        final PsiElement resolved = referenceExpression.resolve();
        if (!GroovyRefactoringUtil.isLocalVariable(resolved)) return;

        if (isOtherTypeOrDifferent(referenceExpression, (GrVariable)resolved) ) {
          final String message = message("local.var.0.is.reassigned", ((GrNamedElement)resolved).getName());
          registerError(referenceExpression, message, LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
      }
    };
  }

  private static boolean isOtherTypeOrDifferent(@NotNull GrReferenceExpression referenceExpression, GrVariable resolved) {
    if (ControlFlowUtils.findControlFlowOwner(referenceExpression) != ControlFlowUtils.findControlFlowOwner(resolved)) return true;

    final PsiType currentType = referenceExpression.getType();
    return currentType != null && currentType != PsiType.NULL && !ControlFlowUtils.findAccess(resolved, referenceExpression, false, true).isEmpty();
  }
}

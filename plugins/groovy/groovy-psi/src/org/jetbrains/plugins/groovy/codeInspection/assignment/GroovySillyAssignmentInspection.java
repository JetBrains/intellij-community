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
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

public class GroovySillyAssignmentInspection extends BaseInspection {

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return "Silly assignment #loc";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@NotNull GrAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);

      if (assignment.isOperatorAssignment()) {
        return;
      }
      final GrExpression lhs = assignment.getLValue();
      final GrExpression rhs = assignment.getRValue();
      if (rhs == null) {
        return;
      }
      if (!(rhs instanceof GrReferenceExpression) || !(lhs instanceof GrReferenceExpression)) {
        return;
      }
      final GrReferenceExpression rhsReference = (GrReferenceExpression) rhs;
      final GrReferenceExpression lhsReference = (GrReferenceExpression) lhs;
      final GrExpression rhsQualifier = rhsReference.getQualifierExpression();
      final GrExpression lhsQualifier = lhsReference.getQualifierExpression();
      if (rhsQualifier != null || lhsQualifier != null) {
        if (!EquivalenceChecker.expressionsAreEquivalent(rhsQualifier, lhsQualifier)) {
          return;
        }
      }
      final String rhsName = rhsReference.getReferenceName();
      final String lhsName = lhsReference.getReferenceName();
      if (rhsName == null || lhsName == null) {
        return;
      }
      if (!rhsName.equals(lhsName)) {
        return;
      }
      final PsiElement rhsReferent = rhsReference.resolve();
      final PsiElement lhsReferent = lhsReference.resolve();
      if (rhsReferent == null || lhsReferent == null || !rhsReferent.equals(lhsReferent)) {
        return;
      }
      registerError(assignment);
    }
  }
}

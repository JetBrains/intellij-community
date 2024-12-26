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
package org.jetbrains.plugins.groovy.codeInspection.threading;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.codeInspection.utils.SideEffectChecker;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSynchronizedStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class GroovyDoubleCheckedLockingInspection extends BaseInspection {

  /**
   * @noinspection PublicField,WeakerAccess
   */
  public boolean ignoreOnVolatileVariables = false;

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return GroovyBundle.message("inspection.message.double.checked.locking");
  }

  @Override
  public @NotNull OptPane getGroovyOptionsPane() {
    return pane(
      checkbox("ignoreOnVolatileVariables", GroovyBundle.message("checkbox.ignore.double.checked.locking.on.volatile.fields")));
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new DoubleCheckedLockingVisitor();
  }

  private class DoubleCheckedLockingVisitor
      extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull GrIfStatement statement) {
      super.visitIfStatement(statement);
      final GrExpression outerCondition = statement.getCondition();
      if (outerCondition == null) {
        return;
      }
      if (SideEffectChecker.mayHaveSideEffects(outerCondition)) {
        return;
      }
      GrStatement thenBranch = statement.getThenBranch();
      if (thenBranch == null) {
        return;
      }
      thenBranch = ControlFlowUtils.stripBraces(thenBranch);
      if (!(thenBranch instanceof GrSynchronizedStatement syncStatement)) {
        return;
      }
      final GrCodeBlock body = syncStatement.getBody();
      if (body == null) {
        return;
      }
      final GrStatement[] statements = body.getStatements();
      if (statements.length != 1) {
        return;
      }
      if (!(statements[0] instanceof GrIfStatement innerIf)) {
        return;
      }
      final GrExpression innerCondition = innerIf.getCondition();
      if (innerCondition == null) {
        return;
      }
      if (!EquivalenceChecker.expressionsAreEquivalent(innerCondition, outerCondition)) {
        return;
      }
      if (ignoreOnVolatileVariables &&
          ifStatementAssignsVolatileVariable(innerIf)) {
        return;
      }
      registerStatementError(statement);
    }

    private static boolean ifStatementAssignsVolatileVariable(
      GrIfStatement statement) {
      GrStatement innerThen = statement.getThenBranch();
      innerThen = ControlFlowUtils.stripBraces(innerThen);
      if (!(innerThen instanceof GrAssignmentExpression assignmentExpression)) {
        return false;
      }
      final GrExpression lhs =
          assignmentExpression.getLValue();
      if (!(lhs instanceof GrReferenceExpression referenceExpression)) {
        return false;
      }
      final PsiElement element =
          referenceExpression.resolve();
      if (!(element instanceof PsiField field)) {
        return false;
      }
      return field.hasModifierProperty(PsiModifier.VOLATILE);
    }
  }
}

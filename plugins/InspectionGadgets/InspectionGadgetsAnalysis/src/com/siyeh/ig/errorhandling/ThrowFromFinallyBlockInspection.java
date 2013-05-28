/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.psi.PsiThrowStatement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class ThrowFromFinallyBlockInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "throw.from.finally.block.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "throw.from.finally.block.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowFromFinallyBlockVisitor();
  }

  private static class ThrowFromFinallyBlockVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitThrowStatement(PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      if (!ControlFlowUtils.isInFinallyBlock(statement)) {
        return;
      }
      registerStatementError(statement);
    }
  }
}
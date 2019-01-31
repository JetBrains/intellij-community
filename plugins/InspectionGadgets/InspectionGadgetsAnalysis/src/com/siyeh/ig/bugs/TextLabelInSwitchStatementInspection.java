/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class TextLabelInSwitchStatementInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("text.label.in.switch.statement.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("text.label.in.switch.statement.problem.descriptor",
                                           infos[0] instanceof PsiSwitchStatement ? 1 : 2);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TextLabelInSwitchStatementVisitor();
  }

  private static class TextLabelInSwitchStatementVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      visitSwitchBlock(statement);
    }

    @Override
    public void visitSwitchExpression(PsiSwitchExpression expression) {
      super.visitSwitchExpression(expression);
      visitSwitchBlock(expression);
    }

    private void visitSwitchBlock(PsiSwitchBlock block) {
      final PsiCodeBlock body = block.getBody();
      if (body == null) {
        return;
      }
      for (PsiStatement statement : body.getStatements()) {
        if (!(statement instanceof PsiLabeledStatement)) {
          continue;
        }
        final PsiLabeledStatement labeledStatement = (PsiLabeledStatement)statement;
        registerError(labeledStatement.getLabelIdentifier(), block);
      }
    }
  }
}
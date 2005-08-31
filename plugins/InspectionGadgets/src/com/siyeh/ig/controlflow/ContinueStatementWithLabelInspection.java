/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiContinueStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class ContinueStatementWithLabelInspection extends StatementInspection {

  public String getGroupDisplayName() {
    return GroupNames.CONTROL_FLOW_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ContinueStatementVisitor();
  }

  private static class ContinueStatementVisitor extends StatementInspectionVisitor {

    public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
      super.visitContinueStatement(statement);
      final PsiIdentifier label = statement.getLabelIdentifier();
      if (label == null) {
        return;
      }
      final String labelText = label.getText();
      if (labelText == null) {
        return;
      }
      if (labelText.length() == 0) {
        return;
      }
      registerStatementError(statement);
    }

  }
}

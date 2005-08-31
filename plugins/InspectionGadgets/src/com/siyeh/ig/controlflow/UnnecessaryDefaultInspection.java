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
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryDefaultInspection extends StatementInspection {

  public String getGroupDisplayName() {
    return GroupNames.CONTROL_FLOW_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryDefaultVisitor();
  }

  private static class UnnecessaryDefaultVisitor
    extends StatementInspectionVisitor {

    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      if (!switchStatementContainsUnnecessaryDefault(statement)) {
        return;
      }
      final PsiCodeBlock body = statement.getBody();
      final PsiStatement[] statements = body.getStatements();
      for (int i = statements.length - 1; i >= 0; i--) {
        final PsiStatement child = statements[i];
        if (child instanceof PsiSwitchLabelStatement) {
          final PsiSwitchLabelStatement label =
            (PsiSwitchLabelStatement)child;
          if (label.isDefaultCase()) {
            registerStatementError(label);
          }
        }
      }
    }

    private static boolean switchStatementContainsUnnecessaryDefault(PsiSwitchStatement statement) {
      final PsiExpression expression = statement.getExpression();
      if (expression == null) {
        return false;
      }
      final PsiType type = expression.getType();
      if (type == null) {
        return false;
      }
      if (!(type instanceof PsiClassType)) {
        return false;
      }
      final PsiClass aClass = ((PsiClassType)type).resolve();
      if (aClass == null) {
        return false;
      }
      if (!aClass.isEnum()) {
        return false;
      }
      final PsiCodeBlock body = statement.getBody();
      if (body == null) {
        return false;
      }
      final PsiStatement[] statements = body.getStatements();
      boolean containsDefault = false;
      int numCases = 0;
      for (final PsiStatement child : statements) {
        if (child instanceof PsiSwitchLabelStatement) {
          if (((PsiSwitchLabelStatement)child).isDefaultCase()) {
            containsDefault = true;
          }
          else {
            numCases++;
          }
        }
      }
      if (!containsDefault) {
        return false;
      }
      final PsiField[] fields = aClass.getFields();
      int numEnums = 0;
      for (final PsiField field : fields) {
        if (field.getType().equals(type)) {
          numEnums++;
        }
      }
      return numEnums == numCases;
    }
  }
}
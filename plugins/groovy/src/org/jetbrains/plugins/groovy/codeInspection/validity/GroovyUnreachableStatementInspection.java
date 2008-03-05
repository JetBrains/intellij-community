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
package org.jetbrains.plugins.groovy.codeInspection.validity;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GroovyUnreachableStatementInspection extends BaseInspection {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return VALIDITY_ISSUES;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Unreachable Statement";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Unreachable statement #loc";

  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    public void visitStatement(GrStatement statement) {
      super.visitStatement(statement);
      final GrStatement prevStatement = PsiTreeUtil.getPrevSiblingOfType(statement, GrStatement.class);
      if (prevStatement == null) {
        return;
      }
      if (!ControlFlowUtils.statementMayCompleteNormally(prevStatement)) {
        registerError(statement);
      }

    }

    public void visitExpression(GrExpression grExpression) {
      final PsiElement parent = grExpression.getParent();
      if (parent instanceof GrCodeBlock) {
        final GrStatement prevStatement = PsiTreeUtil.getPrevSiblingOfType(grExpression, GrStatement.class);
        if (prevStatement == null) {
          return;
        }
        if (!ControlFlowUtils.statementMayCompleteNormally(prevStatement)) {
          registerError(grExpression);
        }
      }
    }
  }
}

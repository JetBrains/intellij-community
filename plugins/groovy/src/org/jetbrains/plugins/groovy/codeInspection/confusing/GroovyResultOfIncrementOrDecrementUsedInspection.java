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
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GroovyResultOfIncrementOrDecrementUsedInspection extends BaseInspection {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Result of increment or decrement used";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Result of increment or decrement expression used #loc";

  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    
    public void visitUnaryExpression(GrUnaryExpression grUnaryExpression) {
      super.visitUnaryExpression(grUnaryExpression);

      final IElementType tokenType = grUnaryExpression.getOperationTokenType();
      if (!GroovyTokenTypes.mINC.equals(tokenType) && !GroovyTokenTypes.mDEC.equals(tokenType)) {
        return;
      }

      final PsiElement parent = PsiTreeUtil.skipParentsOfType(grUnaryExpression, GrParenthesizedExpression.class);
      PsiElement skipped = PsiUtil.skipParentheses(grUnaryExpression, true);
      assert skipped != null;

      if (ControlFlowUtils.collectReturns(ControlFlowUtils.findControlFlowOwner(parent)).contains(skipped)) {
          registerError(grUnaryExpression);
          return;
        }

      if (parent instanceof GrStatementOwner || parent instanceof GrControlStatement) {
        return;
      }

      if (parent instanceof GrTraditionalForClause) {
        if (PsiTreeUtil.isAncestor(((GrTraditionalForClause)parent).getUpdate(), skipped, false)) return;
      }

      registerError(grUnaryExpression);
    }
  }
}
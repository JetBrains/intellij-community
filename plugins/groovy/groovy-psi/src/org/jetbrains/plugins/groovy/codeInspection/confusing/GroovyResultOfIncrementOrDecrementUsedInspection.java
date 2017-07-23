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

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GroovyResultOfIncrementOrDecrementUsedInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Result of increment or decrement used";
  }

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return "Result of increment or decrement expression used #loc";
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitUnaryExpression(@NotNull GrUnaryExpression grUnaryExpression) {
      super.visitUnaryExpression(grUnaryExpression);

      final IElementType tokenType = grUnaryExpression.getOperationTokenType();
      if (!GroovyTokenTypes.mINC.equals(tokenType) && !GroovyTokenTypes.mDEC.equals(tokenType)) {
        return;
      }

      if (PsiUtil.isExpressionUsed(grUnaryExpression)) {
        registerError(grUnaryExpression);
      }
    }
  }
}
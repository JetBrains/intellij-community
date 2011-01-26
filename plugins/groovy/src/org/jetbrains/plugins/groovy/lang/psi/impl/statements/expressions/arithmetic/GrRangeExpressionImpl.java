/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl;

/**
 * @author ilyas
 */
public class GrRangeExpressionImpl extends GrBinaryExpressionImpl implements GrRangeExpression {
  private static final String INTEGER_FQ_NAME = "java.lang.Integer";
  private static final String INT_RANGE_FQ_NAME = "groovy.lang.IntRange";
  private static final String OBJECT_RANGE_FQ_NAME = "groovy.lang.ObjectRange";

  public GrRangeExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public PsiType getType() {
    final PsiType ltype = getLeftOperand().getType();
    if (ltype != null && INTEGER_FQ_NAME.equals(ltype.getCanonicalText())) {
      return getTypeByFQName(INT_RANGE_FQ_NAME);
    }
    return getTypeByFQName(OBJECT_RANGE_FQ_NAME);
  }

  public String toString() {
    return "Range expression";
  }
}

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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiType;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrRangeType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl;

/**
 * @author ilyas
 */
public class GrRangeExpressionImpl extends GrBinaryExpressionImpl implements GrRangeExpression {

  public GrRangeExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  protected Function<GrBinaryExpressionImpl, PsiType> getTypeCalculator() {
    return TYPES_CALCULATOR;
  }

  public String toString() {
    return "Range expression";
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitRangeExpression(this);
  }

  private static final Function<GrBinaryExpressionImpl, PsiType> TYPES_CALCULATOR = new Function<GrBinaryExpressionImpl, PsiType>() {
    @Override
    public PsiType fun(GrBinaryExpressionImpl range) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(range.getProject());

      final GrExpression right = range.getRightOperand();
      final PsiType rtype = right == null ? null : right.getType();
      final PsiType ltype = range.getLeftOperand().getType();

      return new GrRangeType(range.getResolveScope(), facade, ltype, rtype);
    }
  };
}

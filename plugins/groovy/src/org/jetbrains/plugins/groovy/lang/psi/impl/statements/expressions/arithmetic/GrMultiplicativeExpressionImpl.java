/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author ilyas
 */
public class GrMultiplicativeExpressionImpl extends GrBinaryExpressionImpl {
  private static final String DOUBLE_FQ_NAME = "java.lang.Double";
  private static final String FLOAT_FQ_NAME = "java.lang.Float";

  public GrMultiplicativeExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Multiplicative expression";
  }

  public PsiType getType() {
    GrExpression lop = getLeftOperand();
    if (findChildByType(GroovyElementTypes.mDIV) != null) {
      PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
      if (lop != null) {
        PsiType lType = lop.getType();
        if (lType != null && isDoubleOrFloat(lType)) {
          return factory.createTypeByFQClassName(DOUBLE_FQ_NAME, getResolveScope());
        }
      }

      GrExpression rop = getRightOperand();
      if (rop != null) {
        PsiType rType = rop.getType();
        if (rType != null && isDoubleOrFloat(rType)) {
          return factory.createTypeByFQClassName(DOUBLE_FQ_NAME, getResolveScope());
        }
      }

      return factory.createTypeByFQClassName("java.math.BigDecimal", getResolveScope());
    }
    return TypesUtil.getNumericResultType(this, lop == null ? null : lop.getType());
  }

  private boolean isDoubleOrFloat(PsiType type) {
    return type.equalsToText(DOUBLE_FQ_NAME) || type.equalsToText(FLOAT_FQ_NAME);
  }
}

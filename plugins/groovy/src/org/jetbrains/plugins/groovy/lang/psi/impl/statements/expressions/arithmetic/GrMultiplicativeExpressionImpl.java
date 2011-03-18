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
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_DOUBLE;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_FLOAT;

/**
 * @author ilyas
 */
public class GrMultiplicativeExpressionImpl extends GrBinaryExpressionImpl {
  private static final Function<GrBinaryExpressionImpl,PsiType> TYPE_CALCULATOR = new Function<GrBinaryExpressionImpl, PsiType>() {
    @Nullable
    @Override
    public PsiType fun(GrBinaryExpressionImpl binary) {
      PsiType lType = binary.getLeftOperand().getType();
      GrExpression right = binary.getRightOperand();
      PsiType rType = right == null ? null : right.getType();

      if (binary.getOperationTokenType() == GroovyTokenTypes.mDIV) {
        if (isDoubleOrFloat(lType) || isDoubleOrFloat(rType)) {
          return binary.getTypeByFQName(JAVA_LANG_DOUBLE);
        }
        if (TypesUtil.isNumericType(lType) && TypesUtil.isNumericType(rType)) {
          return binary.getTypeByFQName(GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL);
        }
      }

      return TypesUtil.getNumericResultType(binary);
    }
  };

  public GrMultiplicativeExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Multiplicative expression";
  }

  @Override
  protected Function<GrBinaryExpressionImpl, PsiType> getTypeCalculator() {
    return TYPE_CALCULATOR;
  }

  private static boolean isDoubleOrFloat(PsiType type) {
    return type != null && (type.equalsToText(JAVA_LANG_DOUBLE) || type.equalsToText(JAVA_LANG_FLOAT));
  }
}

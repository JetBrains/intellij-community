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
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author ilyas
 */
public class GrAdditiveExpressionImpl extends GrBinaryExpressionImpl {

  private static final Function<GrBinaryExpressionImpl,PsiType> TYPE_CALCULATOR = new Function<GrBinaryExpressionImpl, PsiType>() {
    @Nullable
    @Override
    public PsiType fun(GrBinaryExpressionImpl binary) {
      final PsiType lType = binary.getLeftOperand().getType();
      final PsiType numeric = TypesUtil.getNumericResultType(binary);
      if (numeric != null) return numeric;

      IElementType tokenType = binary.getOperationTokenType();
      if (tokenType == GroovyTokenTypes.mPLUS) {
        if (isStringType(lType)) {
          return binary.getTypeByFQName(CommonClassNames.JAVA_LANG_STRING);
        }
        final GrExpression rop = binary.getRightOperand();
        if (rop != null && isStringType(rop.getType())) {
          return binary.getTypeByFQName(CommonClassNames.JAVA_LANG_STRING);
        }
      }

      return null;
    }
  };

  public GrAdditiveExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  protected Function<GrBinaryExpressionImpl, PsiType> getTypeCalculator() {
    return TYPE_CALCULATOR;
  }

  private static boolean isStringType(PsiType type) {
    return type != null && (type.equalsToText(CommonClassNames.JAVA_LANG_STRING) || type.equalsToText(GroovyCommonClassNames.GROOVY_LANG_GSTRING));
  }

  public String toString() {
    return "Additive expression";
  }
}

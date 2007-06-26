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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

/**
 * @author ilyas
 */
public class GrLiteralImpl extends GrExpressionImpl implements GrLiteral {

  public GrLiteralImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Literal";
  }

  public PsiType getType() {
    IElementType elemType = getFirstChild().getNode().getElementType();
    if (elemType == mGSTRING_LITERAL || elemType == mSTRING_LITERAL || elemType == mREGEX_LITERAL) {
      return getTypeByFQName("java.lang.String");
    } else if (elemType == mNUM_INT) {
      return getTypeByFQName("java.lang.Integer");
    } else if (elemType == mNUM_LONG) {
      return getTypeByFQName("java.lang.Long");
    } else if (elemType == mNUM_FLOAT) {
      return getTypeByFQName("java.lang.Float");
    } else if (elemType == mNUM_DOUBLE) {
      return getTypeByFQName("java.lang.Double");
    } else if (elemType == mNUM_BIG_INT) {
      return getTypeByFQName("java.math.BigInteger");
    } else if (elemType == mNUM_BIG_DECIMAL) {
      return getTypeByFQName("java.math.BigDecimal");
    } else if (elemType == kFALSE || elemType == kTRUE) {
      return getTypeByFQName("java.lang.Boolean");
    }  else if (elemType == kNULL) {
      return PsiType.NULL;
    }

    return null;
  }
}
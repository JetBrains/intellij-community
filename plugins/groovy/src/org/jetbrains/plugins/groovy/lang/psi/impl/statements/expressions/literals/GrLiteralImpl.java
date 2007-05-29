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
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author ilyas
 */
public class GrLiteralImpl extends GroovyPsiElementImpl implements GrLiteral {

  public GrLiteralImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Literal";
  }

  public PsiType getType() {
    IElementType elemType = getFirstChild().getNode().getElementType();
    if (elemType == GroovyTokenTypes.mGSTRING_LITERAL || elemType == GroovyTokenTypes.mSTRING_LITERAL) {
      return getManager().getElementFactory().createTypeByFQClassName("java.lang.String", getResolveScope());
    } else if (elemType == GroovyTokenTypes.mNUM_INT) {
      //todo different token types
      return getManager().getElementFactory().createTypeByFQClassName("java.lang.Integer", getResolveScope());
    } else if (elemType == GroovyTokenTypes.kFALSE || elemType == GroovyTokenTypes.kTRUE) {
      return getManager().getElementFactory().createTypeByFQClassName("java.lang.Boolean", getResolveScope());
    }

    return null;
  }
}
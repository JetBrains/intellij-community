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

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author ilyas
 */
public class GrListOrMapImpl extends GroovyPsiElementImpl implements GrListOrMap {
  private static final TokenSet MAP_LITERAL_TOKENS = TokenSet.create(GroovyElementTypes.ARGUMENT, GroovyTokenTypes.mCOLON);

  public GrListOrMapImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Generalized list";
  }

  public PsiType getType() {
    if (isMapLiteral()) {
      return getManager().getElementFactory().createTypeByFQClassName("java.util.Map", getResolveScope());
    }

    PsiElement parent = getParent();
    if (parent.getParent() instanceof GrVariableDeclaration) {
      GrTypeElement typeElement = ((GrVariableDeclaration) parent.getParent()).getTypeElementGroovy();
      if (typeElement != null) {
        PsiType declaredType = typeElement.getType();
        if (declaredType instanceof PsiArrayType) return declaredType;
      }
    }

    return getManager().getElementFactory().createTypeByFQClassName("java.util.List", getResolveScope());
  }

  private boolean isMapLiteral() {
    return findChildByType(MAP_LITERAL_TOKENS) != null;
  }
}

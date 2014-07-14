/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrWildcardTypeArgument;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 28.03.2007
 */
public class GrWildcardTypeArgumentImpl extends GroovyPsiElementImpl implements GrWildcardTypeArgument {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.types.GrWildcardTypeArgumentImpl");

  public GrWildcardTypeArgumentImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitWildcardTypeArgument(this);
  }

  public String toString() {
    return "Type argument";
  }

  @Override
  @NotNull
  public PsiType getType() {
    final GrTypeElement boundTypeElement = getBoundTypeElement();
    if (boundTypeElement == null) return PsiWildcardType.createUnbounded(getManager());
    if (isExtends()) return PsiWildcardType.createExtends(getManager(), boundTypeElement.getType());
    if (isSuper()) return PsiWildcardType.createSuper(getManager(), boundTypeElement.getType());

    LOG.error("Untested case");
    return null;
  }

  @Override
  public GrTypeElement getBoundTypeElement() {
    return findChildByClass(GrTypeElement.class);
  }

  @Override
  public boolean isExtends() {
    return findChildByType(GroovyTokenTypes.kEXTENDS) != null;
  }

  @Override
  public boolean isSuper() {
    return findChildByType(GroovyTokenTypes.kSUPER) != null;
  }
}

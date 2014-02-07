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
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 28.03.2007
 */
public class GrTypeArgumentListImpl extends GroovyPsiElementImpl implements GrTypeArgumentList {
  public GrTypeArgumentListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTypeArgumentList(this);
  }

  public String toString() {
    return "Type arguments";
  }

  @Override
  public GrTypeElement[] getTypeArgumentElements() {
    return findChildrenByClass(GrTypeElement.class);
  }

  @Override
  public PsiType[] getTypeArguments() {
    final GrTypeElement[] elements = getTypeArgumentElements();
    if (elements.length == 0) return PsiType.EMPTY_ARRAY;

    PsiType[] result = PsiType.createArray(elements.length);
    for (int i = 0; i < elements.length; i++) {
      result[i] = elements[i].getType();
    }
    return result;
  }

  @Override
  public boolean isDiamond() {
    return getTypeArgumentElements().length == 0;
  }

  @Override
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    if (first == last && first.getPsi() instanceof GrTypeElement) {
      if (anchor == null) {
        anchor = getLastChild().getNode();
        before = true;
      }
      if (getTypeArgumentElements().length > 0) {
        if (before == null || before.booleanValue()) {
          getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", anchor);
        }
        else {
          getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", anchor.getTreeNext());
        }
      }

      return super.addInternal(first, last, anchor, before);
    }

    else {
      return super.addInternal(first, last, anchor, before);
    }
  }
}

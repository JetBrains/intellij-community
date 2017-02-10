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
import com.intellij.psi.PsiDisjunctionType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrDisjunctionTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;

/**
 * @author Max Medvedev
 */
public class GrDisjunctionTypeElementImpl extends GroovyPsiElementImpl implements GrDisjunctionTypeElement {
  private volatile PsiType myCachedType;

  public GrDisjunctionTypeElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public GrTypeElement[] getTypeElements() {
    return findChildrenByClass(GrTypeElement.class);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitDisjunctionTypeElement(this);
  }

  @NotNull
  @Override
  public PsiType getType() {
    PsiType cachedType = myCachedType;

    if (cachedType != null) return myCachedType;

    final GrTypeElement[] typeElements = getTypeElements();
    final ArrayList<PsiType> types = new ArrayList<>();
    for (GrTypeElement typeElement : typeElements) {
      types.add(typeElement.getType());
    }
    cachedType = new PsiDisjunctionType(types, getManager());
    myCachedType = cachedType;

    return cachedType;
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedType = null;
  }

  @Override
  public String toString() {
    return "disjunction type element";
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    final PsiElement psi = child.getPsi();
    if (psi instanceof GrTypeElement) {
      PsiElement next = PsiUtil.skipWhitespacesAndComments(psi.getNextSibling(), true);
      if (next != null) {
        if (next.getNode().getElementType() == GroovyTokenTypes.mBOR) {
          super.deleteChildInternal(next.getNode());
        }
      }
      else {
        PsiElement prev = PsiUtil.skipWhitespacesAndComments(psi.getPrevSibling(), false);
        if (prev != null) {
          if (prev.getNode().getElementType() == GroovyTokenTypes.mBOR) {
            super.deleteChildInternal(prev.getNode());
          }
        }
      }
    }

    super.deleteChildInternal(child);
  }
}

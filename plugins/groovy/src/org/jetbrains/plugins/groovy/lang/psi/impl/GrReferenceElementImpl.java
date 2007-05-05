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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public abstract class GrReferenceElementImpl extends GroovyPsiElementImpl implements GrReferenceElement {
  public GrReferenceElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public PsiReference getReference() {
    return this;
  }

  public String getReferenceName() {
    PsiElement nameElement = getReferenceNameElement();
    if (nameElement != null) {
      return nameElement.getText();
    }
    return null;
  }

  public PsiElement getReferenceNameElement() {
    return findChildByType(GroovyTokenTypes.mIDENT);
  }

  public PsiElement getElement() {
    return this;
  }

  public TextRange getRangeInElement() {
    final PsiElement refNameElement = getReferenceNameElement();
    if (refNameElement != null) {
      final int offsetInParent = refNameElement.getStartOffsetInParent();
      return new TextRange(offsetInParent, offsetInParent + refNameElement.getTextLength());
    }
    return new TextRange(0, getTextLength());
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    PsiElement nameElement = getReferenceNameElement();
    if (nameElement != null) {
      ASTNode node = nameElement.getNode();
      ASTNode newNameNode = GroovyElementFactory.getInstance(getProject()).createIdentifierFromText(newElementName).getNode();
      assert newNameNode != null && node != null;
      node.getTreeParent().replaceChild(node, newNameNode);
    }

    return this;
  }
}

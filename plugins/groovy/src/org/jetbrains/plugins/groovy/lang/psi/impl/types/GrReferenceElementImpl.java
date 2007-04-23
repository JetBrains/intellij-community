/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrReferenceElementImpl extends GroovyPsiElementImpl implements GrReferenceElement, PsiReference
{
  public GrReferenceElementImpl(@NotNull ASTNode node)
  {
    super(node);
  }

  public String toString()
  {
    return "Class type";
  }

  public GrReferenceElement getQualifier()
  {
    return (GrReferenceElement) findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  public PsiReference getReference()
  {
    return this;
  }

  public String getReferenceName()
  {
    PsiElement nameElement = getReferenceNameElement();
    if (nameElement != null)
    {
      return nameElement.getText();
    }
    return null;
  }

  private PsiElement getReferenceNameElement()
  {
    return findChildByType(GroovyTokenTypes.mIDENT);
  }

  public PsiElement getElement()
  {
    return this;
  }

  public TextRange getRangeInElement()
  {
    return new TextRange(0, getTextLength());
  }

  @Nullable
  public PsiElement resolve()
  {
    return null;
  }

  public String getCanonicalText()
  {
    PsiElement resolved = resolve();
    if (resolved instanceof GrTypeDefinition)
    {
      return ((GrTypeDefinition) resolved).getQualifiedName();
    }
    if (resolved instanceof PsiPackage)
    {
      return ((PsiPackage) resolved).getQualifiedName();
    }
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException
  {
    PsiElement nameElement = getReferenceNameElement();
    if (nameElement != null)
    {
      ASTNode node = nameElement.getNode();
      ASTNode newNameNode = GroovyElementFactory.getInstance(getProject()).createIdentifierFromText(newElementName).getNode();
      assert newNameNode != null && node != null;
      node.getTreeParent().replaceChild(node, newNameNode);
    }

    return this;
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException
  {
    throw new IncorrectOperationException("NIY");
  }

  public boolean isReferenceTo(PsiElement element)
  {
    if (element instanceof GrTypeDefinition || element instanceof PsiPackage)
    {
      if (Comparing.equal(((PsiNamedElement) element).getName(), getReferenceName()))
      {
        return element.equals(resolve());
      }
    }
    return false;
  }

  public Object[] getVariants()
  {
    return new Object[0];
  }

  public boolean isSoft()
  {
    return false;
  }
}

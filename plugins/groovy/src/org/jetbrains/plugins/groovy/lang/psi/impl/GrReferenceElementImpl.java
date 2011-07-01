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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author ven
 */
public abstract class GrReferenceElementImpl<Q extends PsiElement> extends GroovyPsiElementImpl implements GrReferenceElement<Q> {
  private volatile String myCachedQName = null;
  private volatile String myCachedTextSkipWhiteSpaceAndComments;

  public GrReferenceElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public PsiReference getReference() {
    return this;
  }

  @Override
  public void subtreeChanged() {
    myCachedQName = null;
    myCachedTextSkipWhiteSpaceAndComments = null;
    super.subtreeChanged();
  }

  public String getReferenceName() {
    PsiElement nameElement = getReferenceNameElement();
    if (nameElement != null) {
      return nameElement.getText();
    }
    return null;
  }

  @Nullable
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

  protected PsiElement handleElementRenameInner(String newElementName) throws IncorrectOperationException {
    PsiElement nameElement = getReferenceNameElement();
    if (nameElement != null) {
      ASTNode node = nameElement.getNode();
      ASTNode newNameNode = GroovyPsiElementFactory.getInstance(getProject()).createReferenceNameFromText(newElementName).getNode();
      assert newNameNode != null && node != null;
      node.getTreeParent().replaceChild(node, newNameNode);
    }

    return this;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return handleElementRenameInner(newElementName);
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (isReferenceTo(element)) return this;
    final boolean fullyQualified = isFullyQualified();
    final boolean preserveQualification = CodeStyleSettingsManager.getSettings(getProject()).USE_FQ_CLASS_NAMES && fullyQualified;
    if (element instanceof PsiClass) {
      final String qualifiedName = ((PsiClass)element).getQualifiedName();

      if (!preserveQualification || qualifiedName == null) {
        final String newName = ((PsiClass)element).getName();
        setQualifier(null);
        final GrReferenceElementImpl newElement = ((GrReferenceElementImpl)handleElementRename(newName));

        if (newElement.isReferenceTo(element) || qualifiedName == null || JavaPsiFacade.getInstance(getProject()).findClass(qualifiedName, getResolveScope()) == null) {
          return newElement;
        }
      }

      final GrReferenceElement<Q> qualifiedRef = bindWithQualifiedRef(qualifiedName);
      if (!preserveQualification) {
        GrReferenceAdjuster.shortenReferences(qualifiedRef);
      }
      return qualifiedRef;
    }
    else if (element instanceof PsiMember) {
      PsiMember member = (PsiMember)element;
      if (!isPhysical()) {
        // don't qualify reference: the isReferenceTo() check fails anyway, whether we have a static import for this member or not
        return this;
      }
      final PsiClass psiClass = member.getContainingClass();
      if (psiClass == null) throw new IncorrectOperationException();

      String qName = psiClass.getQualifiedName() + "." + member.getName();
      final GrReferenceElement<Q> qualifiedRef = bindWithQualifiedRef(qName);
      if (!preserveQualification) {
        GrReferenceAdjuster.shortenReferences(qualifiedRef);
      }
      return qualifiedRef;
    }
    else if (element instanceof PsiPackage) {
      return bindWithQualifiedRef(((PsiPackage)element).getQualifiedName());
    }

    throw new IncorrectOperationException("Cannot bind to:" + element + " of class " + element.getClass());
  }


  protected abstract GrReferenceElement<Q> bindWithQualifiedRef(@NotNull String qName);

  protected boolean bindsCorrectly(PsiElement element) {
    return isReferenceTo(element);
  }

  public abstract boolean isFullyQualified();

  @NotNull
  public PsiType[] getTypeArguments() {
    final GrTypeArgumentList typeArgsList = getTypeArgumentList();
    if (typeArgsList == null) return PsiType.EMPTY_ARRAY;

    final GrTypeElement[] args = typeArgsList.getTypeArgumentElements();
    if (args.length == 0) return PsiType.EMPTY_ARRAY;
    PsiType[] result = new PsiType[args.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = args[i].getType();
    }

    return result;
  }

  @Nullable
  public GrTypeArgumentList getTypeArgumentList() {
    return (GrTypeArgumentList)findChildByType(GroovyElementTypes.TYPE_ARGUMENTS);
  }

  public void setQualifier(@Nullable Q newQualifier) {
    PsiImplUtil.setQualifier(this, newQualifier);
  }

  public String getClassNameText() {
    String cachedQName = myCachedQName;
    if (cachedQName == null) {
      myCachedQName = cachedQName = PsiNameHelper.getQualifiedClassName(getTextSkipWhiteSpaceAndComments(), false);
    }
    return cachedQName;
  }

  protected String getTextSkipWhiteSpaceAndComments() {
    String whiteSpaceAndComments = myCachedTextSkipWhiteSpaceAndComments;
    if (whiteSpaceAndComments == null) {
      myCachedTextSkipWhiteSpaceAndComments = whiteSpaceAndComments = PsiImplUtil.getTextSkipWhiteSpaceAndComments(getNode());
    }
    return whiteSpaceAndComments;
  }

}

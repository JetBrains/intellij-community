/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTagValueToken;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.CompletionProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

/**
 * @author ilyas
 */
public abstract class GrDocMemberReferenceImpl extends GroovyDocPsiElementImpl implements GrDocMemberReference {
  public GrDocMemberReferenceImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  public GrDocReferenceElement getReferenceHolder() {
    return findChildByClass(GrDocReferenceElement.class);
  }

  public boolean isReferenceTo(PsiElement element) {
    return getManager().areElementsEquivalent(element, resolve());
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (isReferenceTo(element)) return this;
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    PsiElement nameElement = getReferenceNameElement();
    ASTNode node = nameElement.getNode();
    ASTNode newNameNode = GroovyPsiElementFactory.getInstance(getProject()).createDocMemberReferenceNameFromText(newElementName).getNode();
    assert newNameNode != null && node != null;
    node.getTreeParent().replaceChild(node, newNameNode);
    return this;
  }

  @NotNull
  public GrDocTagValueToken getReferenceNameElement() {
    GrDocTagValueToken token = findChildByClass(GrDocTagValueToken.class);
    assert token != null;
    return token;
  }

  public PsiElement getElement() {
    return this;
  }

  public PsiReference getReference() {
    return this;
  }

  public TextRange getRangeInElement() {
    final PsiElement refNameElement = getReferenceNameElement();
    final int offsetInParent = refNameElement.getStartOffsetInParent();
    return new TextRange(offsetInParent, offsetInParent + refNameElement.getTextLength());
  }

  public String getCanonicalText() {
    return null;
  }

  public boolean isSoft() {
    return false;
  }

  @Nullable
  public PsiElement getQualifier() {
    return getReferenceHolder();
  }

  @Nullable
  @NonNls
  public String getReferenceName() {
    return getReferenceNameElement().getText();
  }

  @Nullable
  public PsiElement resolve() {
    ResolveResult[] results = multiResolve(false);
    if (results.length == 1) {
      return results[0].getElement();
    }
    return null;
  }

  @NotNull
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    return multiResolveImpl();
  }

  public Object[] getVariants() {
    GrDocReferenceElement holder = getReferenceHolder();
    PsiElement resolved;
    if (holder != null) {
      GrCodeReferenceElement referenceElement = holder.getReferenceElement();
      resolved = referenceElement != null ? referenceElement.resolve() : null;
    } else {
      resolved = getEnclosingClass(this);
    }
    if (resolved instanceof PsiClass) {
      ResolverProcessor propertyProcessor = CompletionProcessor.createPropertyCompletionProcessor(this);
      resolved.processDeclarations(propertyProcessor, PsiSubstitutor.EMPTY, null, this);
      PsiElement[] propertyCandidates = ResolveUtil.mapToElements(propertyProcessor.getCandidates());
      PsiType thisType = getManager().getElementFactory().createType((PsiClass) resolved, PsiSubstitutor.EMPTY);
      ResolverProcessor methodProcessor = CompletionProcessor.createPropertyCompletionProcessor(this);

      resolved.processDeclarations(methodProcessor, PsiSubstitutor.EMPTY, null, this);

      PsiElement[] methodCandidates = ResolveUtil.mapToElements(methodProcessor.getCandidates());

      PsiElement[] elements = ArrayUtil.mergeArrays(propertyCandidates, methodCandidates, PsiElement.class);

      return ContainerUtil.map2Array(elements, new Function<PsiElement, Object>() {
        public Object fun(PsiElement psiElement) {
          LookupItem<PsiElement> lookupItem = null;
          if (psiElement instanceof PsiNamedElement) {
            if (psiElement instanceof PsiMethod) {
              lookupItem = new LookupItem<PsiElement>(psiElement, ((PsiMethod) psiElement).getName());
            } else {
              String string = ((PsiNamedElement) psiElement).getName();
              lookupItem = new LookupItem<PsiElement>(psiElement, string == null ? "" : string);
            }
            lookupItem.setAttribute(LookupItem.DO_AUTOCOMPLETE_ATTR, null);
            lookupItem.setAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR, true);
          }
          return lookupItem != null ? lookupItem : psiElement;
        }
      });
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  protected PsiClass getEnclosingClass(PsiElement element) {
    PsiElement parent = element.getParent();
    while (parent != null) {
      if (parent instanceof GrTypeDefinition) return (PsiClass) parent;
      if (parent instanceof GroovyFile) return ((GroovyFile) parent).getScriptClass();
      parent = parent.getParent();
    }
    return null;
  }

  protected abstract ResolveResult[] multiResolveImpl();

}

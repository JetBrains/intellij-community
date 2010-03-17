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

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 04.04.2007
 */
public class GrAnnotationNameValuePairImpl extends GroovyPsiElementImpl implements GrAnnotationNameValuePair, PsiReference {
  public GrAnnotationNameValuePairImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAnnotationNameValuePair(this);
  }

  public String toString() {
    return "Annotation member value pair";
  }

  @Nullable
  public String getName() {
    final PsiElement nameId = getNameIdentifierGroovy();
    return nameId != null ? nameId.getText() : null;
  }

  @Nullable
  public PsiElement getNameIdentifierGroovy() {
    return findChildByType(GroovyElementTypes.mIDENT);
  }

  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  public GrAnnotationMemberValue getValue() {
    return findChildByClass(GrAnnotationMemberValue.class);
  }

  @NotNull
  public PsiAnnotationMemberValue setValue(@NotNull PsiAnnotationMemberValue newValue) {
    getValue().replace(newValue);
    return getValue();
  }

  public PsiReference getReference() {
    return this;
  }

  public PsiElement getElement() {
    return this;
  }

  public TextRange getRangeInElement() {
    PsiElement nameId = getNameIdentifierGroovy();
    if (nameId != null) {
      return nameId.getTextRange().shiftRight(-getTextRange().getStartOffset());
    }
    return new TextRange(0, 0);
  }

  @Nullable
  public PsiElement resolve() {
    GrAnnotation anno = getAnnotation();
    if (anno != null) {
      GrCodeReferenceElement ref = anno.getClassReference();
      if (ref != null) {
        PsiElement resolved = ref.resolve();
        if (resolved instanceof PsiClass && ((PsiClass) resolved).isAnnotationType()) {
          String declaredName = getName();
          String name = declaredName == null ? PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME : declaredName;
          PsiMethod[] methods = ((PsiClass) resolved).findMethodsByName(name, false);
          return methods.length == 1 ? methods[0] : null;
        }
      }
    }
    return null;
  }

  private GrAnnotation getAnnotation() {
    PsiElement pParent = getParent().getParent();
    if (pParent instanceof GrAnnotation) return (GrAnnotation) pParent;
    PsiElement ppParent = pParent.getParent();
    return ppParent instanceof GrAnnotation ? (GrAnnotation)ppParent : null;
  }

  public String getCanonicalText() {
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    PsiElement nameElement = getNameIdentifierGroovy();
    ASTNode newNameNode = GroovyPsiElementFactory.getInstance(getProject()).createReferenceNameFromText(newElementName).getNode();
    assert newNameNode != null;
    if (nameElement != null) {
      ASTNode node = nameElement.getNode();
      assert node != null;
      getNode().replaceChild(node, newNameNode);
    } else {
      PsiElement first = getFirstChild();
      ASTNode anchorBefore = first != null ? first.getNode() : null;
      getNode().addLeaf(GroovyElementTypes.mASSIGN, "=", anchorBefore);
      getNode().addChild(newNameNode, anchorBefore);
    }

    return this;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("NYI");
  }

  public boolean isReferenceTo(PsiElement element) {
    return element instanceof PsiMethod && getManager().areElementsEquivalent(element, resolve());
  }

  @NotNull
  public Object[] getVariants() {
    GrAnnotation anno = getAnnotation();
    if (anno != null) {
      GrCodeReferenceElement ref = anno.getClassReference();
      if (ref != null) {
        PsiElement resolved = ref.resolve();
        if (resolved instanceof PsiClass && ((PsiClass) resolved).isAnnotationType()) {
          return ((PsiClass) resolved).getMethods();
        }
      }
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return false;
  }
}

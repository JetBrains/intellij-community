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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

/**
 * @author Max Medvedev
 */
public class GrSyntheticTypeElement extends LightElement implements PsiTypeElement {
  @NotNull private final GrTypeElement myElement;

  public GrSyntheticTypeElement(@NotNull GrTypeElement element) {
    super(element.getManager(), element.getLanguage());

    myElement = element;
  }

  @NotNull
  @Override
  public PsiType getType() {
    return myElement.getType();
  }

  @Override
  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    return null;
  }

  @NotNull
  @Override
  public PsiAnnotation[] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiAnnotation[] getApplicableAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return null;
  }

  @NotNull
  @Override
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return "Synthetic PsiTypeElement";
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    if (newElement instanceof PsiTypeElement) {
      GrTypeElement groovyTypeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(((PsiTypeElement)newElement).getType());
      return myElement.replace(groovyTypeElement);
    }
    else {
      return super.replace(newElement);
    }
  }

  @Override
  public TextRange getTextRange() {
    return myElement.getTextRange();
  }

  @Override
  public int getTextOffset() {
    return myElement.getTextOffset();
  }

  @Override
  public String getText() {
    return myElement.getText();
  }

  @Override
  public boolean isValid() {
    return myElement.isValid();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeElement(this);
    }
  }

  @Override
  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    final PsiElement[] children = myElement.getChildren();
    for (PsiElement child : children) {
      if (child instanceof GrTypeElement) {
        PsiImplUtil.getOrCreateTypeElement((GrTypeElement)child).accept(visitor);
      }
    }
  }
}

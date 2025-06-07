// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final @NotNull GrTypeElement myElement;

  public GrSyntheticTypeElement(@NotNull GrTypeElement element) {
    super(element.getManager(), element.getLanguage());

    myElement = element;
  }

  @Override
  public @NotNull PsiType getType() {
    return myElement.getType();
  }

  @Override
  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    return null;
  }

  @Override
  public PsiAnnotation @NotNull [] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  public PsiAnnotation @NotNull [] getApplicableAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return null;
  }

  @Override
  public @NotNull PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
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
    else {
      visitor.visitElement(this);
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

  @Override
  public boolean acceptsAnnotations() {
    return false;
  }
}

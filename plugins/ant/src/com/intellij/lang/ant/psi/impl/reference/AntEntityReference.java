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
package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public class AntEntityReference implements PsiReference {

  private final AntElement myElement;
  private final PsiReference myXmlRef;

  public AntEntityReference(final AntElement element, final PsiReference xmlRef) {
    myElement = element;
    myXmlRef = xmlRef;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    return myXmlRef.getRangeInElement();
  }

  @Nullable
  public PsiElement resolve() {
    return myXmlRef.resolve();
  }

  public String getCanonicalText() {
    return myXmlRef.getCanonicalText();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return myXmlRef.handleElementRename(newElementName);
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return myXmlRef.bindToElement(element);
  }

  public boolean isReferenceTo(PsiElement element) {
    return myXmlRef.isReferenceTo(element);
  }

  @NotNull
  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }

  public boolean isSoft() {
    return myXmlRef.isSoft();
  }
}

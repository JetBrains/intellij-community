/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for references in String literals.
 */
public abstract class StringLiteralReference implements PsiReference {
  protected final PsiLiteral myValue;

  public StringLiteralReference(PsiLiteral value) {
    myValue = value;
  }

  @Override
  public @NotNull PsiElement getElement() {
    return myValue;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return ElementManipulators.getValueTextRange(myValue);
  }

  @Override
  public @NotNull String getCanonicalText() {
    return myValue.getText();
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    return myValue;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return myValue;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return resolve() == element;
  }

  protected @Nullable String getValue() {
    return (String)myValue.getValue();
  }

}

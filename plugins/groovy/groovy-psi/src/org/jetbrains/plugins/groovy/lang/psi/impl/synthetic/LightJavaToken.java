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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.tree.IElementType;

/**
 * @author Medvedev Max
 */
public class LightJavaToken extends LightElement implements PsiJavaToken {
  private final PsiElement myElement;
  private final IElementType myType;

  public LightJavaToken(PsiElement element, IElementType type) {
    super(element.getManager(), element.getLanguage());
    myElement = element;
    myType = type;
  }

  @Override
  public boolean isValid() {
    return myElement.isValid();
  }

  @Override
  public String toString() {
    return "light java token";
  }

  @Override
  public TextRange getTextRange() {
    return myElement.getTextRange();
  }

  @Override
  public PsiFile getContainingFile() {
    return myElement.getContainingFile();
  }

  @Override
  public int getStartOffsetInParent() {
    return myElement.getStartOffsetInParent();
  }

  @Override
  public int getTextOffset() {
    return myElement.getTextOffset();
  }

  @Override
  public PsiElement getParent() {
    return myElement.getParent();
  }

  @Override
  public PsiElement getPrevSibling() {
    return myElement.getPrevSibling();
  }

  @Override
  public PsiElement getNextSibling() {
    return myElement.getNextSibling();
  }

  @Override
  public PsiElement copy() {
    return new LightJavaToken(myElement, myType);
  }

  @Override
  public IElementType getTokenType() {
    return myType;
  }
}

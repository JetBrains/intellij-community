/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.CachingReference;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 13, 2010
 */
public abstract class AntDomReference extends CachingReference{
  private final AntDomElement myAntElement;
  private final String myText;
  private final TextRange myTextRange;

  protected AntDomReference(final AntDomElement element, final String text, final TextRange textRange) {
    myAntElement = element;
    myText = text;
    myTextRange = textRange;
  }

  public AntDomElement getAntElement() {
    return myAntElement;
  }

  public PsiElement getElement() {
    return myAntElement.getXmlElement();
  }

  public TextRange getRangeInElement() {
    return myTextRange;
  }

  public String getCanonicalText() {
    return myText;
  }

  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public PsiElement resolveInner() {
    throw new UnsupportedOperationException();
  }

  public String getUnresolvedMessagePattern() {
    return CodeInsightBundle.message("error.cannot.resolve.default.message", "");
  }

}

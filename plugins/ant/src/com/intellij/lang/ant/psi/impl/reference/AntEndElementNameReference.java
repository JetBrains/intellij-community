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

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AntEndElementNameReference extends AntElementNameReference {
  private final TextRange myRange;
  private final boolean myIsTagClosed;

  public AntEndElementNameReference(final AntStructuredElement element, TextRange range, final boolean isClosed) {
    super(element);
    myRange = range;
    myIsTagClosed = isClosed;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return getElement();
  }

  @NotNull
  public LookupElement[] getVariants() {
    final AntStructuredElement element = getElement();
    final XmlTag xmlTag = element.getSourceElement();
    final String completionText = myIsTagClosed ? element.getSourceElement().getName() : element.getSourceElement().getName() + ">";
    final AntElementCompletionWrapper wrapper =
        new AntElementCompletionWrapper((AntElement)element.getParent(), completionText, element.getProject(), AntElementRole.TASK_ROLE) {
          public PsiElement getContext() {
            return xmlTag;
          }
        };
    return new LookupElement[] {AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE.applyPolicy(
      LookupElementBuilder.create(wrapper, completionText))};
  }

  public TextRange getRangeInElement() {
    return myRange;
  }
}

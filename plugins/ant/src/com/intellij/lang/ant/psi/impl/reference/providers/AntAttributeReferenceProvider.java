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
package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.misc.PsiReferenceListSpinAllocator;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.reference.AntAttributeReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AntAttributeReferenceProvider extends PsiReferenceProvider {

  @SuppressWarnings({"HardCodedStringLiteral"})
  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    if (!(element instanceof AntStructuredElement)) {
      return PsiReference.EMPTY_ARRAY;
    }
    final AntStructuredElement se = (AntStructuredElement)element;
    final int elementStartOffset = se.getTextRange().getStartOffset();
    final List<PsiReference> list = PsiReferenceListSpinAllocator.alloc();
    try {
      for (PsiElement child : se.getSourceElement().getChildren()) {
        if (child instanceof XmlToken) {
          XmlToken token = (XmlToken)child;
          // TODO: move XmlTokenType to openAPI
          if (token.getTokenType().toString().equals("XML_TAG_END")) {
            break;
          }
        }
        else if (child instanceof PsiWhiteSpace) {
          final int off = child.getTextRange().getStartOffset() - elementStartOffset + 1;
          list.add(new AntAttributeReference(se, " ", new TextRange(off, off), null));
        }
        else if (child instanceof XmlAttribute) {
          final PsiElement nameElement = child.getFirstChild();
          if (nameElement != null) {
            final int off = nameElement.getTextRange().getStartOffset() - elementStartOffset;
            final String text = nameElement.getText();
            list.add(new AntAttributeReference(se, text, new TextRange(off, off + text.length()), null));
          }
        }
      }
      final int count = list.size();
      return (count == 0) ? PsiReference.EMPTY_ARRAY : list.toArray(new PsiReference[count]);
    }
    finally {
      PsiReferenceListSpinAllocator.dispose(list);
    }
  }

}

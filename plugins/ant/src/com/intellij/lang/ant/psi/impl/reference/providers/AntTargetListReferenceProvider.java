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
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.lang.ant.psi.impl.reference.AntTargetReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AntTargetListReferenceProvider extends PsiReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    final AntTarget target = (AntTarget)element;
    final XmlAttribute attr = target.getSourceElement().getAttribute("depends", null);
    if (attr == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final XmlAttributeValue xmlAttributeValue = attr.getValueElement();
    if (xmlAttributeValue == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    int offsetInPosition = xmlAttributeValue.getTextRange().getStartOffset() - target.getTextRange().getStartOffset() + 1;
    final String value = attr.getValue();
    final List<PsiReference> result = PsiReferenceListSpinAllocator.alloc();
    try {
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        int i = 0;
        int rightBound;
        final int valueLen = value.length();
        do {
          rightBound = (i < valueLen) ? value.indexOf(',', i) : valueLen;
          if (rightBound < 0) rightBound = valueLen;
          builder.setLength(0);
          int j = i;
          for (; j < rightBound; ++j) {
            builder.append(value.charAt(j));
          }
          j = 0;
          final int len = builder.length();
          for (; j < len; ++j) {
            if (!Character.isWhitespace(builder.charAt(j))) break;
          }
          final String targetName = (len == 0 || j == len) ? "" : builder.substring(j);
          result.add(new AntTargetReference(target, targetName,
                                            new TextRange(offsetInPosition + i + j, offsetInPosition + i + j + targetName.length()), attr));
          i = rightBound + 1;
        }
        while (rightBound < valueLen);
        return (result.size() > 0) ? result.toArray(new PsiReference[result.size()]) : PsiReference.EMPTY_ARRAY;
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }
    finally {
      PsiReferenceListSpinAllocator.dispose(result);
    }
  }

}

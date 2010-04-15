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

import com.intellij.lang.ant.misc.PsiReferenceListSpinAllocator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomReferenceInjector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
* @author Eugene Zhuravlev
*         Date: Apr 9, 2010
*/
class AntReferenceInjector implements DomReferenceInjector {
  public String resolveString(@Nullable String unresolvedText, @NotNull ConvertContext context) {
    final DomElement element = context.getInvocationElement();
    return null;
  }

  @NotNull
  public PsiReference[] inject(@Nullable String unresolvedText, @NotNull PsiElement element, @NotNull ConvertContext context) {
    return PsiReference.EMPTY_ARRAY;
  }

  private List<PsiReference> getDependentTargetReferences(AntDomTarget target) {
    final String xmlAttributeValue = target.getDependsList().getValue();
    if (xmlAttributeValue == null) {
      return Collections.emptyList();
    }
    final XmlAttribute attr = target.getDependsList().getXmlAttribute();
    if (attr == null) {
      return Collections.emptyList();
    }
    final int offsetInPosition = attr.getValueTextRange().getStartOffset();
    final List<PsiReference> result = PsiReferenceListSpinAllocator.alloc();
    try {
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        int i = 0;
        int rightBound;
        final int valueLen = xmlAttributeValue.length();
        do {
          rightBound = (i < valueLen) ? xmlAttributeValue.indexOf(',', i) : valueLen;
          if (rightBound < 0) rightBound = valueLen;
          builder.setLength(0);
          int j = i;
          for (; j < rightBound; ++j) {
            builder.append(xmlAttributeValue.charAt(j));
          }
          j = 0;
          final int len = builder.length();
          for (; j < len; ++j) {
            if (!Character.isWhitespace(builder.charAt(j))) break;
          }
          final String targetName = (len == 0 || j == len) ? "" : builder.substring(j);
          final int start = offsetInPosition + i + j;
          result.add(new AntDomDependentTargetReference(target, targetName, start));
          i = rightBound + 1;
        }
        while (rightBound < valueLen);
        return result;
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

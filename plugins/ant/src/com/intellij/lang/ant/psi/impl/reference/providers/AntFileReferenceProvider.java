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

import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.reference.AntFileReferenceSet;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AntFileReferenceProvider extends PsiReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    AntStructuredElement antElement = (AntStructuredElement)element;
    final List<String> referenceAttributes = antElement.getFileReferenceAttributes();
    if (referenceAttributes.isEmpty()) {
      return PsiReference.EMPTY_ARRAY;
    }
    final List<PsiReference> refList = new ArrayList<PsiReference>();
    for (String attrib : referenceAttributes) {
      final XmlAttribute attr = antElement.getSourceElement().getAttribute(attrib, null);
      if (attr == null) {
        continue;
      }
      final XmlAttributeValue xmlAttributeValue = attr.getValueElement();
      if (xmlAttributeValue == null) {
        continue;
      }
      final String attrValue = attr.getValue();
      if (attrValue == null || attrValue.length() == 0 || isSingleSlash(attrValue) || attrValue.indexOf("@{") >= 0) {
        continue;
      }
      final AntFileReferenceSet refSet = new AntFileReferenceSet(antElement, xmlAttributeValue, this);
      ContainerUtil.addAll(refList, refSet.getAllReferences());
    }
    return refList.toArray(new PsiReference[refList.size()]);
  }

  private static boolean isSingleSlash(final String attrValue) {
    return "/".equals(attrValue) || "\\".equals(attrValue);
  }

}

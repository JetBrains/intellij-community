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
import com.intellij.lang.ant.psi.impl.reference.AntRefIdReference;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

public class AntRefIdReferenceProvider extends PsiReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    if (!(element instanceof AntStructuredElement)) {
      return PsiReference.EMPTY_ARRAY;
    }
    final AntStructuredElement se = (AntStructuredElement)element;
    final List<PsiReference> refs = new ArrayList<PsiReference>();
    for (XmlAttribute attr : se.getSourceElement().getAttributes()) {
      if (!isRefAttribute(se, attr.getName())) {
        continue;
      }
      final XmlAttributeValue valueElement = attr.getValueElement();
      if (valueElement == null) {
        continue;
      }
      final int offsetInPosition = valueElement.getTextRange().getStartOffset() - se.getTextRange().getStartOffset() + 1;
      final String attrValue = attr.getValue();
      if (attrValue == null || attrValue.indexOf("@{") >= 0) {
        continue;
      }
      refs.add(new AntRefIdReference(se, attrValue, new TextRange(offsetInPosition, offsetInPosition + attrValue.length()), attr));
    }
    return refs.toArray(new PsiReference[refs.size()]);
  }

  private static boolean isRefAttribute(AntStructuredElement element, @NonNls final String attribName) {
    if ("refid".equals(attribName)) {
      return true;
    }
    final AntTypeDefinition typeDef = element.getTypeDefinition();
    return typeDef != null && AntAttributeType.ID_REFERENCE == typeDef.getAttributeType(attribName);
  }

}
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

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.impl.AntAntImpl;
import com.intellij.lang.ant.psi.impl.AntCallImpl;
import com.intellij.lang.ant.psi.impl.AntProjectImpl;
import com.intellij.lang.ant.psi.impl.reference.AntTargetReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class AntSingleTargetReferenceProvider extends PsiReferenceProvider {

  @NonNls private static final Map<Class,String> ourTypesToAttributeNames;

  static {
    ourTypesToAttributeNames = new HashMap<Class, String>();
    ourTypesToAttributeNames.put(AntProjectImpl.class, "default");
    ourTypesToAttributeNames.put(AntCallImpl.class, "target");
    ourTypesToAttributeNames.put(AntAntImpl.class, "target");
  }

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    String attributeName = ourTypesToAttributeNames.get(element.getClass());
    if( attributeName == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    AntElement antElement = (AntElement)element;
    final XmlAttribute attr = ((XmlTag)antElement.getSourceElement()).getAttribute(attributeName, null);
    if (attr == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final XmlAttributeValue valueElement = attr.getValueElement();
    if( valueElement == null ) {
      return PsiReference.EMPTY_ARRAY;
    }
    final int offsetInPosition = valueElement.getTextRange().getStartOffset() - antElement.getTextRange().getStartOffset() + 1;
    final String attrValue = valueElement.getValue();
    return new PsiReference[]{
      new AntTargetReference(antElement, attrValue, new TextRange(offsetInPosition, offsetInPosition + attrValue.length()), attr)};
  }

}

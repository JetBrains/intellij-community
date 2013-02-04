/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;

import java.util.HashMap;
import java.util.Map;

/**
* User: anna
*/
class JavaFxComponentIdReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                               @NotNull ProcessingContext context) {
    final XmlAttributeValue xmlAttributeValue = (XmlAttributeValue)element;
    final XmlTag currentTag = PsiTreeUtil.getParentOfType(xmlAttributeValue, XmlTag.class);
    final String value = xmlAttributeValue.getValue();
    final boolean startsWithDollar = value.startsWith("$");
    final String referencesId = startsWithDollar ? value.substring(1) : value;
    final Map<String, XmlAttributeValue> fileIds = new HashMap<String, XmlAttributeValue>();
    xmlAttributeValue.getContainingFile().accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);
        if (currentTag != tag) {
          final XmlAttribute attribute = tag.getAttribute(FxmlConstants.FX_ID);
          if (attribute != null) {
            fileIds.put(attribute.getValue(), attribute.getValueElement());
          }
        }
      }
    });

    return new PsiReference[]{new PsiReferenceBase<XmlAttributeValue>(xmlAttributeValue) {
      @Override
      public TextRange getRangeInElement() {
        final TextRange rangeInElement = super.getRangeInElement();
        return startsWithDollar ? new TextRange(rangeInElement.getStartOffset() + 1, rangeInElement.getEndOffset()) : rangeInElement;
      }

      @Nullable
      @Override
      public PsiElement resolve() {
        return fileIds.get(referencesId);
      }

      @NotNull
      @Override
      public Object[] getVariants() {
        return ArrayUtil.toStringArray(fileIds.keySet());
      }
    }};
  }
}

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

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyAttributeDescriptor;

/**
* User: anna
*/
class EnumeratedAttributeReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (element instanceof XmlAttributeValue) {
      final XmlAttributeValue xmlAttributeValue = (XmlAttributeValue)element;
      final PsiElement parent = xmlAttributeValue.getParent();
      if (parent instanceof XmlAttribute) {
        final XmlAttributeDescriptor descriptor = ((XmlAttribute)parent).getDescriptor();
        if (descriptor instanceof JavaFxPropertyAttributeDescriptor && descriptor.isEnumerated()) {
          final PsiField enumConstant = ((JavaFxPropertyAttributeDescriptor)descriptor).getEnumConstant(xmlAttributeValue.getValue());
          return new PsiReference[] {new PsiReferenceBase<XmlAttributeValue>(xmlAttributeValue){
            @Nullable
            @Override
            public PsiElement resolve() {
              return enumConstant;
            }

            @NotNull
            @Override
            public Object[] getVariants() {
              return ArrayUtil.EMPTY_OBJECT_ARRAY;
            }
          }};
        }
      }
    }
     return PsiReference.EMPTY_ARRAY;
  }
}

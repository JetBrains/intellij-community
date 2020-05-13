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
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyAttributeDescriptor;

class JavaFxColorReferenceProvider extends PsiReferenceProvider {

  @Override
  public boolean acceptsTarget(@NotNull PsiElement target) {
    if (!(target instanceof PsiField)) return false;

    PsiClass psiClass = ((PsiField)target).getContainingClass();
    return psiClass != null && JavaFxCommonNames.JAVAFX_SCENE_COLOR.equals(psiClass.getQualifiedName());
  }

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                         @NotNull ProcessingContext context) {
    final XmlAttributeValue attributeValue = (XmlAttributeValue)element;
    final PsiElement parent = attributeValue.getParent();
    if (parent instanceof XmlAttribute) {
      final XmlAttributeDescriptor descriptor = ((XmlAttribute)parent).getDescriptor();
      if (descriptor instanceof JavaFxPropertyAttributeDescriptor) {
        final PsiElement declaration = descriptor.getDeclaration();
        final PsiClassType propertyClassType = JavaFxPsiUtil.getPropertyClassType(declaration);
        if (propertyClassType != null && InheritanceUtil.isInheritor(propertyClassType, JavaFxCommonNames.JAVAFX_SCENE_PAINT)) {
          return new PsiReference[] {new JavaFxColorReference(attributeValue)};
        }
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }
}

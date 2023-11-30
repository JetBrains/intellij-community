// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

final class JavaFxColorReferenceProvider extends PsiReferenceProvider {

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

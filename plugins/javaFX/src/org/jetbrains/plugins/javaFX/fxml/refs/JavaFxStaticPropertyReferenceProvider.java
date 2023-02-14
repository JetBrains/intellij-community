// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

public class JavaFxStaticPropertyReferenceProvider extends PsiReferenceProvider {
  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (!(element instanceof XmlAttribute xmlAttribute) || !JavaFxFileTypeFactory.isFxml(element.getContainingFile())) return PsiReference.EMPTY_ARRAY;

    final String name = xmlAttribute.getName();
    final int dotIndex = name.indexOf('.');
    if (dotIndex <= 0 || name.indexOf('.', dotIndex + 1) >= 0) return PsiReference.EMPTY_ARRAY;
    final String className = name.substring(0, dotIndex);
    final JavaFxStaticPropertyClassReference classReference = new JavaFxStaticPropertyClassReference(xmlAttribute, className);
    classReference.setRangeInElement(new TextRange(0, dotIndex));
    if (dotIndex + 1 == name.length()) {
      return new PsiReference[]{classReference};
    }
    final String propertyName = name.substring(dotIndex + 1);
    final JavaFxStaticPropertyReference methodReference =
      new JavaFxStaticPropertyReference(xmlAttribute, classReference.getPsiClass(), propertyName);
    methodReference.setRangeInElement(new TextRange(dotIndex + 1, name.length()));

    return new PsiReference[]{classReference, methodReference};
  }

  private static class JavaFxStaticPropertyClassReference extends PsiReferenceBase<XmlAttribute> {
    private final PsiClass myPsiClass;

    JavaFxStaticPropertyClassReference(@NotNull XmlAttribute xmlAttribute, @NotNull String className) {
      super(xmlAttribute);
      myPsiClass = JavaFxPsiUtil.findPsiClass(className, xmlAttribute);
    }

    public PsiClass getPsiClass() {
      return myPsiClass;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return myPsiClass;
    }
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.refactoring;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.beanProperties.BeanPropertyElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.JavaFXBundle;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxPropertyReference;

public final class JavaFxPropertyElement extends BeanPropertyElement {
  private final JavaFxPropertyReference myPropertyReference;

  private JavaFxPropertyElement(PsiMethod method, String propertyName, JavaFxPropertyReference propertyReference) {
    super(method, propertyName);
    myPropertyReference = propertyReference;
  }

  @Nullable
  @Override
  public PsiType getPropertyType() {
    return myPropertyReference.getType();
  }

  @Override
  public String getTypeName() {
    return JavaFXBundle.message("javafx.refactoring.property.element.type.name");
  }

  @NotNull
  public JavaFxPropertyReference getPropertyReference() {
    return myPropertyReference;
  }

  @Nullable
  static PsiElement fromReference(@NotNull final JavaFxPropertyReference propertyReference) {
    final PsiElement element = propertyReference.resolve();
    if (element instanceof PsiMethod) {
      final String propertyName = propertyReference.getPropertyName();
      if (propertyName != null) {
        return new JavaFxPropertyElement((PsiMethod)element, propertyName, propertyReference);
      }
    }
    if (element instanceof PsiField) {
      return element;
    }
    return null;
  }
}

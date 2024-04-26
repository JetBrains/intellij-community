// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

public abstract class JavaFxPropertyReference<T extends PsiElement> extends PsiReferenceBase<T> {
  protected final PsiClass myPsiClass;

  public JavaFxPropertyReference(@NotNull T element, PsiClass aClass) {
    super(element);
    myPsiClass = aClass;
  }

  public @Nullable PsiMethod getGetter() {
    if (myPsiClass == null) return null;
    return JavaFxPsiUtil.findPropertyGetter(myPsiClass, getPropertyName());
  }

  public @Nullable PsiMethod getSetter() {
    if (myPsiClass == null) return null;
    return JavaFxPsiUtil.findInstancePropertySetter(myPsiClass, getPropertyName());
  }

  public @Nullable PsiField getField() {
    if (myPsiClass == null) return null;
    return myPsiClass.findFieldByName(getPropertyName(), true);
  }

  public @Nullable PsiMethod getObservableGetter() {
    if (myPsiClass == null) return null;
    return JavaFxPsiUtil.findObservablePropertyGetter(myPsiClass, getPropertyName());
  }

  public @Nullable PsiMethod getStaticSetter() {
    return null;
  }

  public @Nullable PsiType getType() {
    return JavaFxPsiUtil.getReadablePropertyType(resolve());
  }

  public abstract @Nullable String getPropertyName();
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

public final class JavaFxStaticPropertyReference extends JavaFxPropertyReference<XmlAttribute> {
  private final String myPropertyName;
  private final PsiMethod myStaticMethod;

  public JavaFxStaticPropertyReference(@NotNull XmlAttribute xmlAttribute,
                                       @Nullable PsiClass psiClass,
                                       @NotNull String propertyName) {
    super(xmlAttribute, psiClass);
    myPropertyName = propertyName;
    myStaticMethod = JavaFxPsiUtil.findStaticPropertySetter(propertyName, psiClass);
  }

  @Override
  public @Nullable PsiElement resolve() {
    return myStaticMethod;
  }

  @Override
  public @Nullable PsiMethod getGetter() {
    return null;
  }

  @Override
  public @Nullable PsiMethod getSetter() {
    return null;
  }

  @Override
  public @Nullable PsiField getField() {
    return null;
  }

  @Override
  public @Nullable PsiMethod getObservableGetter() {
    return null;
  }

  @Override
  public @Nullable PsiMethod getStaticSetter() {
    return myStaticMethod;
  }

  @Override
  public PsiType getType() {
    if (myStaticMethod != null) {
      final PsiParameter[] parameters = myStaticMethod.getParameterList().getParameters();
      if (parameters.length == 2) {
        return parameters[1].getType();
      }
    }
    return null;
  }

  @Override
  public String getPropertyName() {
    return myPropertyName;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    final String newPropertyName = JavaFxPsiUtil.getPropertyName(newElementName, true);
    return super.handleElementRename(newPropertyName);
  }
}

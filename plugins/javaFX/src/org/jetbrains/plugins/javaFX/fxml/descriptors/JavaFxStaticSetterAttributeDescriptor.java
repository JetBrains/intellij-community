// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.PsiUtil;

public final class JavaFxStaticSetterAttributeDescriptor extends JavaFxPropertyAttributeDescriptor {
  private final PsiMethod mySetter;
  
  public JavaFxStaticSetterAttributeDescriptor(PsiMethod setter, String name) {
    super(name, setter.getContainingClass());
    mySetter = setter;
  }

  @Override
  public PsiElement getDeclaration() {
    return mySetter;
  }

  @Override
  protected PsiClass getEnum() {
    if (mySetter != null) {
      final PsiParameter[] parameters = mySetter.getParameterList().getParameters();
      if (parameters.length == 2) {
        final PsiClass enumClass = PsiUtil.resolveClassInType(parameters[1].getType());
        return enumClass != null && enumClass.isEnum() ? enumClass : null;
      }
    }
    return null;
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.Nullable;

public final class JavaFxSetterAttributeDescriptor extends JavaFxPropertyAttributeDescriptor {
  private final PsiMethod myPsiMethod;

  public JavaFxSetterAttributeDescriptor(PsiMethod psiMethod, PsiClass psiClass) {
    super(psiClass.getName() + "." + StringUtil.decapitalize(psiMethod.getName().substring("set".length())), psiClass);
    myPsiMethod = psiMethod;
  }

  @Override
  public String @Nullable [] getEnumeratedValues() {
    return null;
  }

  @Override
  public @Nullable String validateValue(XmlElement context, String value) {
    return null;
  }

  @Override
  public PsiElement getDeclaration() {
    return myPsiMethod != null && myPsiMethod.isValid() ? myPsiMethod : null;
  }
}

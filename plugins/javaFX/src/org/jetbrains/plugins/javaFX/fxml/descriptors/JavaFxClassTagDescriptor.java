// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

public final class JavaFxClassTagDescriptor extends JavaFxClassTagDescriptorBase {
  private final PsiClass myPsiClass;

  public JavaFxClassTagDescriptor(String name, XmlTag tag) {
    this(name, JavaFxPsiUtil.findPsiClass(name, tag));
  }

  public JavaFxClassTagDescriptor(String name, PsiClass psiClass) {
    super(name);
    myPsiClass = psiClass;
  }

  @Override
  public PsiClass getPsiClass() {
    return myPsiClass;
  }

  @Override
  public String toString() {
    return myPsiClass != null ? "<" + myPsiClass.getName() + ">" : "<" + getName() + "?>";
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxClassTagDescriptor extends JavaFxClassTagDescriptorBase {
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

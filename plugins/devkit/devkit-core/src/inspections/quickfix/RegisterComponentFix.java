// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.psi.PsiClass;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.util.ComponentType;

public class RegisterComponentFix extends AbstractRegisterFix {
  private final ComponentType myType;

  public RegisterComponentFix(ComponentType type, @NotNull SmartPsiElementPointer<PsiClass> pointer) {
    super(pointer);
    myType = type;
  }

  @Override
  protected String getType() {
    return DevKitBundle.message(myType.myPropertyKey);
  }

  @Override
  public void patchPluginXml(XmlFile pluginXml, PsiClass aClass) throws IncorrectOperationException {
    myType.patchPluginXml(pluginXml, aClass);
  }
}

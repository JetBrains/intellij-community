/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  protected String getType() {
    return DevKitBundle.message(myType.myPropertyKey);
  }

  public void patchPluginXml(XmlFile pluginXml, PsiClass aClass) throws IncorrectOperationException {
    myType.patchPluginXml(pluginXml, aClass);
  }
}

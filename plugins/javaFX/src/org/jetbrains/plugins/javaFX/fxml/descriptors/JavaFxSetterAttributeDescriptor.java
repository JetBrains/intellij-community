/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: 1/10/13
 */
public class JavaFxSetterAttributeDescriptor extends JavaFxPropertyAttributeDescriptor {
  private final PsiMethod myPsiMethod;

  public JavaFxSetterAttributeDescriptor(PsiMethod psiMethod, PsiClass psiClass) {
    super(psiClass.getName() + "." + StringUtil.decapitalize(psiMethod.getName().substring("set".length())), psiClass);
    myPsiMethod = psiMethod;
  }

  @Nullable
  @Override
  public String[] getEnumeratedValues() {
    return null;
  }

  @Nullable
  @Override
  public String validateValue(XmlElement context, String value) {
    return null;
  }

  @Override
  public PsiElement getDeclaration() {
    return myPsiMethod != null && myPsiMethod.isValid() ? myPsiMethod : null;
  }
}

/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author ven
 */
public class DefaultGroovyMethod extends LightMethod {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultGroovyMethod");

  private PsiMethod myMethod;
  private PsiMethod myModifiedMethod;

  @NotNull
  public Language getLanguage() {
    return GroovyFileType.GROOVY_FILE_TYPE.getLanguage();
  }

  public boolean isValid() {
    return true;
  }

  public DefaultGroovyMethod(PsiMethod method, boolean isStatic) {
    super(method.getManager(), method, null);
    myMethod = method;
    PsiElementFactory elementFactory = method.getManager().getElementFactory();
    try {
      myModifiedMethod = elementFactory.createMethod(method.getName(), method.getReturnType());
      myModifiedMethod.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
      myModifiedMethod.getModifierList().setModifierProperty(PsiModifier.STATIC, isStatic);
      PsiParameter[] originalParameters = method.getParameterList().getParameters();
      PsiParameterList newParamList = myModifiedMethod.getParameterList();
      for (int i = 1; i < originalParameters.length; i++) {
        PsiParameter originalParameter = originalParameters[i];
        PsiParameter parameter = elementFactory.createParameter("p" + i, originalParameter.getType());
        newParamList.add(parameter);
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @NotNull
  public PsiParameterList getParameterList() {
    return myModifiedMethod.getParameterList();
  }

  @NotNull
  public PsiModifierList getModifierList() {
    return myModifiedMethod.getModifierList();
  }

  public boolean hasModifierProperty(String name) {
    return myModifiedMethod.hasModifierProperty(name);
  }

  public boolean canNavigate() {
    return myMethod.canNavigate();
  }

  public void navigate(boolean requestFocus) {
    myMethod.navigate(requestFocus);
  }
}

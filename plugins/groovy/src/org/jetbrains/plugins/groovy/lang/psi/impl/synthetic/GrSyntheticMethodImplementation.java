/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.*;
import org.jetbrains.plugins.groovy.GroovyIcons;

/**
 * @author peter
 */
public class GrSyntheticMethodImplementation extends GrSyntheticMethod {
  private final PsiMethod myInterfaceMethod;

  public GrSyntheticMethodImplementation(PsiMethod interfaceMethod, PsiClass containingClass) {
    super(interfaceMethod.getManager(), interfaceMethod.getName());
    myInterfaceMethod = interfaceMethod;
    setContainingClass(containingClass);
    setNavigationElement(interfaceMethod);
    for (PsiParameter psiParameter : myInterfaceMethod.getParameterList().getParameters()) {
      addParameter(psiParameter);
    }
    setReturnType(myInterfaceMethod.getReturnType());
    setModifiers(PsiModifier.PUBLIC);
    setBaseIcon(GroovyIcons.METHOD);
  }

  public String toString() {
    return "SyntheticMethodImplementation";
  }

  @Override
  public PsiElement getContext() {
    return myInterfaceMethod;
  }

}

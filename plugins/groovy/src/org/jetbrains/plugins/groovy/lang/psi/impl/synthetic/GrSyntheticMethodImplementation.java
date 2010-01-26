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
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collections;
import java.util.Set;

/**
 * @author peter
 */
public class GrSyntheticMethodImplementation extends GrSyntheticMethod {
  private final PsiMethod myInterfaceMethod;
  private final PsiClass myContainingClass;

  public GrSyntheticMethodImplementation(PsiMethod interfaceMethod, PsiClass containingClass) {
    super(interfaceMethod.getManager(), interfaceMethod.getName());
    myInterfaceMethod = interfaceMethod;
    myContainingClass = containingClass;
  }

  protected LightParameter[] getParameters() {
    return ContainerUtil.map2Array(myInterfaceMethod.getParameterList().getParameters(), LightParameter.class, new Function<PsiParameter, LightParameter>() {
      public LightParameter fun(PsiParameter psiParameter) {
        return new LightParameter(myManager, psiParameter.getName(), psiParameter.getNameIdentifier(), psiParameter.getType(), myInterfaceMethod);
      }
    });
  }

  protected Set<String> getModifiers() {
    return Collections.singleton(PsiModifier.PUBLIC);
  }

  public PsiElement copy() {
    return new GrSyntheticMethodImplementation(myInterfaceMethod, myContainingClass);
  }

  public PsiType getReturnType() {
    return myInterfaceMethod.getReturnType();
  }

  public PsiIdentifier getNameIdentifier() {
    return myInterfaceMethod.getNameIdentifier();
  }

  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  public PsiFile getContainingFile() {
    return myContainingClass.getContainingFile();
  }

  public int getTextOffset() {
    return myInterfaceMethod.getTextOffset();
  }

  @Override
  public PsiElement getNavigationElement() {
    return myInterfaceMethod;
  }


  public String toString() {
    return "SyntheticMethodImplementation";
  }

  @Override
  public PsiElement getContext() {
    return myInterfaceMethod;
  }
}

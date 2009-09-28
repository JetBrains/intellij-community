/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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

}

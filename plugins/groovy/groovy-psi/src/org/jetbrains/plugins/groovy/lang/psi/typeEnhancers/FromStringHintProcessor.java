/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.List;

public class FromStringHintProcessor extends SignatureHintProcessor {

  @Override
  public String getHintName() {
    return "groovy.transform.stc.FromString";
  }

  @NotNull
  @Override
  public List<PsiType[]> inferExpectedSignatures(@NotNull final PsiMethod method,
                                                 @NotNull final PsiSubstitutor substitutor,
                                                 @NotNull String[] options) {
    PsiElement context = createContext(method);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
    return ContainerUtil.map(options, value -> {
      try {
        PsiType original = factory.createTypeFromText("SomeUnexpectedDummyClass<" + value + ">", context);
        if (original instanceof PsiClassType) {
          PsiType[] parameters = ((PsiClassType)original).getParameters();
          return ContainerUtil.map(parameters, substitutor::substitute).toArray(new PsiType[parameters.length]) ;
        }
      }
      catch (IncorrectOperationException e) {
        //do nothing. Just don't throw an exception
      }
      return new PsiType[]{PsiType.NULL};
    });
  }

  @NotNull
  public static PsiElement createContext(@NotNull PsiMethod method) {
    return new FromStringLightElement(method);
  }
}

class FromStringLightElement extends LightElement {

  private final PsiMethod myMethod;
  private final GroovyFile myFile;

  FromStringLightElement(@NotNull PsiMethod method) {
    super(method.getManager(), GroovyLanguage.INSTANCE);
    myMethod = method;
    myFile = GroovyPsiElementFactory.getInstance(getProject()).createGroovyFile("", false, null);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!ResolveUtil.shouldProcessClasses(processor.getHint(ElementClassHint.KEY))) return true;

    for (PsiTypeParameter parameter : myMethod.getTypeParameters()) {
      if (!ResolveUtil.processElement(processor, parameter, state)) return false;
    }

    PsiClass containingClass = myMethod.getContainingClass();
    if (containingClass != null) {
      PsiTypeParameter[] parameters = containingClass.getTypeParameters();
      for (PsiTypeParameter parameter : parameters) {
        if (!ResolveUtil.processElement(processor, parameter, state)) return false;
      }
    }

    return myFile.processDeclarations(processor, state, lastParent, place);
  }

  @Override
  public String toString() {
    return "fromStringLightElement";
  }
}

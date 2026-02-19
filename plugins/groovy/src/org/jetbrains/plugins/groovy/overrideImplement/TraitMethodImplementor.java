// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.overrideImplement;

import com.intellij.codeInsight.MethodImplementor;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.util.GroovyOverrideImplementUtil;

public final class TraitMethodImplementor implements MethodImplementor {
  @Override
  public PsiMethod @NotNull [] createImplementationPrototypes(PsiClass inClass, PsiMethod method) throws IncorrectOperationException {
    if (!(inClass instanceof GrTypeDefinition && method instanceof GrTraitMethod)) return PsiMethod.EMPTY_ARRAY;

    final PsiClass containingClass = method.getContainingClass();
    PsiSubstitutor substitutor = inClass.isInheritor(containingClass, true) ? TypeConversionUtil.getSuperClassSubstitutor(containingClass, inClass, PsiSubstitutor.EMPTY)
                                                                            : PsiSubstitutor.EMPTY;
    return new GrMethod[]{
      GroovyOverrideImplementUtil.generateTraitMethodPrototype((GrTypeDefinition)inClass, (GrTraitMethod)method, substitutor)};
  }

  @Override
  public GenerationInfo createGenerationInfo(PsiMethod method, boolean mergeIfExists) {
    return null;
  }

  @Override
  public @NotNull Consumer<PsiMethod> createDecorator(PsiClass targetClass,
                                                      PsiMethod baseMethod,
                                                      boolean toCopyJavaDoc,
                                                      boolean insertOverrideIfPossible) {
    return new GroovyMethodImplementor.PsiMethodConsumer(targetClass, toCopyJavaDoc, baseMethod, insertOverrideIfPossible);
  }

  @Override
  public PsiMethod @NotNull [] getMethodsToImplement(PsiClass aClass) {
    return PsiMethod.EMPTY_ARRAY;
  }
}

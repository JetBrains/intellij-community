// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.CurryKt;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class GrClosureType extends GrLiteralClassType {

  private final List<GrSignature> mySignatures;
  private volatile PsiType[] myTypeArgs;

  private GrClosureType(@NotNull LanguageLevel languageLevel,
                        @NotNull GlobalSearchScope scope,
                        @NotNull JavaPsiFacade facade,
                        @NotNull List<GrSignature> signatures,
                        boolean shouldInferTypeParameters) {
    super(languageLevel, scope, facade);
    mySignatures = signatures;
    if (!shouldInferTypeParameters) myTypeArgs = PsiType.EMPTY_ARRAY;
  }

  private GrClosureType(@NotNull LanguageLevel level,
                        @NotNull GlobalSearchScope scope,
                        @NotNull JavaPsiFacade facade,
                        @NotNull List<GrSignature> signatures,
                        @Nullable PsiType[] typeArgs) {
    super(level, scope, facade);
    mySignatures = signatures;
    myTypeArgs = typeArgs;
  }

  @Override
  public int getParameterCount() {
    PsiClass resolved = resolve();
    return resolved != null && resolved.getTypeParameters().length == 1 ? 1 : 0;
  }

  @Override
  @NotNull
  public PsiType[] getParameters() {
    if (ourForbidClosureInference) throw new IllegalStateException();
    if (myTypeArgs == null) {
      myTypeArgs = inferParameters();
    }
    return myTypeArgs;
  }

  @NotNull
  public PsiType[] inferParameters() {
    final PsiClass psiClass = resolve();
    if (psiClass != null && psiClass.getTypeParameters().length == 1) {
      final PsiType type = GrClosureSignatureUtil.getReturnType(mySignatures);
      if (type == PsiType.NULL || type == null) {
        return new PsiType[]{null};
      }
      else {
        return new PsiType[]{TypesUtil.boxPrimitiveType(type, getPsiManager(), getResolveScope(), true)};
      }
    }
    else {
      return PsiType.EMPTY_ARRAY;
    }
  }

  @NotNull
  @Override
  protected String getJavaClassName() {
    return GroovyCommonClassNames.GROOVY_LANG_CLOSURE;
  }

  @Override
  @NotNull
  public PsiClassType rawType() {
    if (myTypeArgs != null && myTypeArgs.length == 0) {
      return this;
    }

    return new GrClosureType(getLanguageLevel(), getResolveScope(), myFacade, mySignatures, false);
  }

  @Override
  public boolean isValid() {
    return ContainerUtil.all(mySignatures, GrSignature::isValid);
  }

  public boolean equals(Object obj) {
    if (obj instanceof GrClosureType) {
      return Comparing.equal(mySignatures, ((GrClosureType)obj).mySignatures);
    }

    return super.equals(obj);
  }

  @Override
  @NotNull
  public PsiClassType setLanguageLevel(@NotNull final LanguageLevel languageLevel) {
    return new GrClosureType(languageLevel, myScope, myFacade, mySignatures, myTypeArgs);
  }

  @NotNull
  public static GrClosureType create(@NotNull Iterable<? extends GroovyResolveResult> results, @NotNull GroovyPsiElement context) {
    List<GrSignature> signatures = new ArrayList<>();
    for (GroovyResolveResult result : results) {
      if (result.getElement() instanceof PsiMethod) {
        signatures.add(GrClosureSignatureUtil.createSignature((PsiMethod)result.getElement(), result.getSubstitutor()));
      }
    }

    final GlobalSearchScope resolveScope = context.getResolveScope();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());
    return create(signatures, resolveScope, facade, LanguageLevel.JDK_1_5, true);
  }

  public static GrClosureType create(@NotNull GrFunctionalExpression expression, boolean shouldInferTypeParameters) {
    final GrSignature signature = GrClosureSignatureUtil.createSignature(expression);
    final GlobalSearchScope resolveScope = expression.getResolveScope();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(expression.getProject());
    return create(Collections.singletonList(signature), resolveScope, facade, LanguageLevel.JDK_1_5, shouldInferTypeParameters);
  }

  @NotNull
  public static GrClosureType create(@NotNull List<GrSignature> signatures,
                                     GlobalSearchScope scope,
                                     JavaPsiFacade facade,
                                     @NotNull LanguageLevel languageLevel,
                                     boolean shouldInferTypeParameters) {
    return new GrClosureType(languageLevel, scope, facade, signatures, shouldInferTypeParameters);
  }

  @Nullable
  public PsiType curry(@NotNull PsiType[] args, int position, @NotNull PsiElement context) {
    final List<GrSignature> curried = CurryKt.curry(mySignatures, args, position, context);
    if (curried.isEmpty()) {
      return null;
    }
    return new GrClosureType(myLanguageLevel, myScope, myFacade, curried, myTypeArgs);
  }

  @NotNull
  public List<GrSignature> getSignatures() {
    return mySignatures;
  }

  @Override
  public String toString() {
    return "PsiType: Closure<*>";
  }

  private static boolean ourForbidClosureInference;

  @TestOnly
  public static void forbidClosureInference(@NotNull Runnable runnable) {
    ourForbidClosureInference = true;
    try {
      runnable.run();
    }
    finally {
      ourForbidClosureInference = false;
    }
  }
}
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class GrClosureType extends GrLiteralClassType {
  private final GrSignature mySignature;
  private volatile PsiType[] myTypeArgs;
  private GrClosableBlock myClosure;

  private GrClosureType(@NotNull LanguageLevel languageLevel,
                        @NotNull GlobalSearchScope scope,
                        @NotNull JavaPsiFacade facade,
                        @NotNull GrSignature closureSignature,
                        boolean shouldInferTypeParameters) {
    super(languageLevel, scope, facade);
    mySignature = closureSignature;
    if (!shouldInferTypeParameters) myTypeArgs = PsiType.EMPTY_ARRAY;
  }

  private GrClosureType(@NotNull LanguageLevel level,
                        @NotNull GlobalSearchScope scope,
                        @NotNull JavaPsiFacade facade,
                        @NotNull GrSignature signature,
                        @Nullable PsiType[] typeArgs) {
    super(level, scope, facade);

    mySignature = signature;
    myTypeArgs = typeArgs;
  }

  @Nullable
  public GrClosableBlock getClosure() {
    return myClosure;
  }

  private void setClosure(@NotNull GrClosableBlock closure) {
    myClosure = closure;
  }

  @Override
  public int getParameterCount() {
    PsiClass resolved = resolve();
    return resolved != null && resolved.getTypeParameters().length == 1 ? 1 : 0;
  }

  @Override
  @NotNull
  public PsiType[] getParameters() {
    if (myTypeArgs == null) {
      myTypeArgs = inferParameters();
    }
    return myTypeArgs;
  }

  @NotNull
  public PsiType[] inferParameters() {
    final PsiClass psiClass = resolve();
    if (psiClass != null && psiClass.getTypeParameters().length == 1) {
      final PsiType type = GrClosureSignatureUtil.getReturnType(mySignature);
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

    return new GrClosureType(getLanguageLevel(), getResolveScope(), myFacade, mySignature, false);
  }

  @Override
  public boolean isValid() {
    return mySignature.isValid();
  }

  public boolean equals(Object obj) {
    if (obj instanceof GrClosureType) {
      return Comparing.equal(mySignature, ((GrClosureType)obj).mySignature);
    }

    return super.equals(obj);
  }

  @Override
  @NotNull
  public PsiClassType setLanguageLevel(@NotNull final LanguageLevel languageLevel) {
    return new GrClosureType(languageLevel, myScope, myFacade, mySignature, myTypeArgs);
  }

  @NotNull
  public static GrClosureType create(@NotNull Iterable<? extends GroovyResolveResult> results, @NotNull GroovyPsiElement context) {
    List<GrClosureSignature> signatures = new ArrayList<>();
    for (GroovyResolveResult result : results) {
      if (result.getElement() instanceof PsiMethod) {
        signatures.add(GrClosureSignatureUtil.createSignature((PsiMethod)result.getElement(), result.getSubstitutor()));
      }
    }

    final GlobalSearchScope resolveScope = context.getResolveScope();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());
    if (signatures.size() == 1) {
      return create(signatures.get(0), resolveScope, facade, LanguageLevel.JDK_1_5, true);
    }
    else {
      return create(GrClosureSignatureUtil.createMultiSignature(signatures.toArray(GrClosureSignature.EMPTY_ARRAY)),
                    resolveScope, facade, LanguageLevel.JDK_1_5, true);
    }
  }

  public static GrClosureType create(@NotNull GrClosableBlock closure, boolean shouldInferTypeParameters) {
    final GrClosureSignature signature = GrClosureSignatureUtil.createSignature(closure);
    final GlobalSearchScope resolveScope = closure.getResolveScope();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(closure.getProject());
    GrClosureType type = create(signature, resolveScope, facade, LanguageLevel.JDK_1_5, shouldInferTypeParameters);
    type.setClosure(closure);
    return type;
  }

  public static GrClosureType create(@NotNull GrSignature signature,
                                     GlobalSearchScope scope,
                                     JavaPsiFacade facade,
                                     @NotNull LanguageLevel languageLevel,
                                     boolean shouldInferTypeParameters) {
    return new GrClosureType(languageLevel, scope, facade, signature, shouldInferTypeParameters);
  }

  @Nullable
  public PsiType curry(@NotNull PsiType[] args, int position, @NotNull GroovyPsiElement context) {
    final GrSignature newSignature = mySignature.curry(args, position, context);
    if (newSignature == null) return null;
    return new GrClosureType(myLanguageLevel, myScope, myFacade, newSignature, myTypeArgs);
  }

  @NotNull
  public GrSignature getSignature() {
    return mySignature;
  }

  @Override
  public String toString() {
    return "PsiType: Closure<*>";
  }
}
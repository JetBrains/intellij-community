/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  private GrClosureType(LanguageLevel languageLevel,
                        @NotNull GlobalSearchScope scope,
                        @NotNull JavaPsiFacade facade,
                        @NotNull GrSignature closureSignature,
                        boolean shouldInferTypeParameters) {
    super(languageLevel, scope, facade);
    mySignature = closureSignature;
    if (!shouldInferTypeParameters) myTypeArgs = PsiType.EMPTY_ARRAY;
  }

  private GrClosureType(LanguageLevel level,
                        @NotNull GlobalSearchScope scope,
                        @NotNull JavaPsiFacade facade,
                        @NotNull GrSignature signature,
                        @Nullable PsiType[] typeArgs) {
    super(level, scope, facade);

    mySignature = signature;
    myTypeArgs = typeArgs;
  }

  @Override
  @NotNull
  public String getClassName() {
    return "Closure";
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
  private PsiType[] inferParameters() {
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
  @NotNull
  public String getInternalCanonicalText() {
    return getCanonicalText();
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

  public static GrClosureType create(GroovyResolveResult[] results, GroovyPsiElement context) {
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
      return create(GrClosureSignatureUtil.createMultiSignature(signatures.toArray(new GrClosureSignature[signatures.size()])),
                    resolveScope, facade, LanguageLevel.JDK_1_5, true);
    }
  }

  public static GrClosureType create(@NotNull GrClosableBlock closure, boolean shouldInferTypeParameters) {
    final GrClosureSignature signature = GrClosureSignatureUtil.createSignature(closure);
    final GlobalSearchScope resolveScope = closure.getResolveScope();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(closure.getProject());
    return create(signature, resolveScope, facade,LanguageLevel.JDK_1_5, shouldInferTypeParameters);
  }

  public static GrClosureType create(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor) {
    final GrClosureSignature signature = GrClosureSignatureUtil.createSignature(method, substitutor);
    final GlobalSearchScope scope = GlobalSearchScope.allScope(method.getProject());
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(method.getProject());
    return create(signature, scope, facade, LanguageLevel.JDK_1_5, true);
  }

  public static GrClosureType create(@NotNull PsiParameter[] parameters,
                                     @Nullable PsiType returnType,
                                     JavaPsiFacade facade,
                                     GlobalSearchScope scope,
                                     LanguageLevel languageLevel) {
    return create(GrClosureSignatureUtil.createSignature(parameters, returnType), scope, facade, languageLevel, true);
  }

  public static GrClosureType create(@NotNull GrSignature signature,
                                     GlobalSearchScope scope,
                                     JavaPsiFacade facade,
                                     LanguageLevel languageLevel,
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
}

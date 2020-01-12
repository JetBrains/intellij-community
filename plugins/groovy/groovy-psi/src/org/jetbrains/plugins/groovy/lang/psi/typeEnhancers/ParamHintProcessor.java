// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class ParamHintProcessor extends SignatureHintProcessor {
  private final int myParam;
  private final int myGeneric;
  private final String myHint;

  public ParamHintProcessor(String hint, int param, int generic) {
    myHint = hint;
    myParam = param;
    myGeneric = generic;
  }

  @Override
  public String getHintName() {
    return myHint;
  }

  @NotNull
  @Override
  public List<PsiType[]> inferExpectedSignatures(@NotNull PsiMethod method,
                                                 @NotNull PsiSubstitutor substitutor,
                                                 String @NotNull [] options) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (myParam < parameters.length) {
      PsiParameter parameter = parameters[myParam];
      PsiType originalType = parameter.getType();

      PsiType substituted = substitutor.substitute(originalType);

      if (myGeneric == -1) {
        return produceResult(substituted);
      }
      else {
        if (substituted instanceof PsiClassType) {
          PsiType[] typeParameters = ((PsiClassType)substituted).getParameters();
          if (myGeneric < typeParameters.length) {
            return produceResult(typeParameters[myGeneric]);
          }
          //if (substituted.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          //  return produceResult(TypesUtil.createType(CommonClassNames.JAVA_LANG_CHARACTER, method));
          //}
        }
      }
    }
    return ContainerUtil.emptyList();
  }

  @NotNull
  protected static ArrayList<PsiType[]> produceResult(@Nullable PsiType type) {
    PsiType notNull = type != null ? type : PsiType.NULL;
    PsiType[] signature = {notNull};
    ArrayList<PsiType[]> result = new ArrayList<>();
    result.add(signature);
    return result;
  }
}

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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Max Medvedev on 28/02/14
 */
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
                                                 @NotNull String[] options) {
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
    return ContainerUtilRt.emptyList();
  }

  @NotNull
  protected static ArrayList<PsiType[]> produceResult(@Nullable PsiType type) {
    PsiType notNull = type != null ? type : PsiType.NULL;
    PsiType[] signature = {notNull};
    ArrayList<PsiType[]> result = ContainerUtil.newArrayList();
    result.add(signature);
    return result;
  }
}

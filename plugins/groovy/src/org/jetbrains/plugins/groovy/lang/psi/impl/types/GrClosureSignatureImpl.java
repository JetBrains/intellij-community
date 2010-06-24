/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Maxim.Medvedev
 */
public class GrClosureSignatureImpl implements GrClosureSignature {
  private final boolean myIsVarargs;
  @Nullable private final PsiType myReturnType;
  @NotNull private final GrClosureParameter[] myParameters;
  @NotNull private PsiSubstitutor mySubstitutor;

  public GrClosureSignatureImpl(@NotNull PsiParameter[] parameters, @Nullable PsiType returnType, @NotNull PsiSubstitutor substitutor) {
    myReturnType = substitutor.substitute(returnType);
    final int length = parameters.length;
    myParameters = new GrClosureParameter[length];
    for (int i = 0; i < length; i++) {
      myParameters[i] = new GrClosureParameterImpl(parameters[i], substitutor);
    }
    if (length > 0) {
      myIsVarargs = /*parameters[length - 1].isVarArgs() ||*/ myParameters[length - 1].getType() instanceof PsiArrayType;
    }
    else {
      myIsVarargs = false;
    }
    mySubstitutor = substitutor;
  }

  public GrClosureSignatureImpl(PsiParameter[] parameters, PsiType returnType) {
    this(parameters, returnType, PsiSubstitutor.EMPTY);
  }

  public GrClosureSignatureImpl(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor) {
    this(method.getParameterList().getParameters(), PsiUtil.getSmartReturnType(method), substitutor);
  }

  GrClosureSignatureImpl(@NotNull GrClosureParameter[] params, @Nullable PsiType returnType, boolean isVarArgs) {
    myParameters = params;
    myReturnType = returnType;
    myIsVarargs = isVarArgs;
  }


  public boolean isVarargs() {
    return myIsVarargs;
  }


  @Nullable
  public PsiType getReturnType() {
    return myReturnType;
  }

  @NotNull
  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  @NotNull
  public GrClosureParameter[] getParameters() {
    GrClosureParameter[] result = new GrClosureParameter[myParameters.length];
    System.arraycopy(myParameters, 0, result, 0, myParameters.length);
    return result;
  }

  @Nullable
  public GrClosureSignature curry(int count) {
    if (count > myParameters.length) {
      if (isVarargs()) {
        return new DerivedClosureSignature();
      }
      else {
        return null;
      }
    }
    GrClosureParameter[] newParams = new GrClosureParameter[myParameters.length - count];
    System.arraycopy(myParameters, count, newParams, 0, newParams.length);
    return new DerivedClosureSignature(newParams, null, myIsVarargs);
  }

  public boolean isValid() {
    for (GrClosureParameter parameter : myParameters) {
      if (!parameter.isValid()) return false;
    }
    final PsiType returnType = getReturnType();
    return returnType == null || returnType.isValid();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof GrClosureSignature) {
      return Comparing.equal(myParameters, ((GrClosureSignature)obj).getParameters()) &&
             Comparing.equal(myIsVarargs, ((GrClosureSignature)obj).isVarargs());
    }
    return super.equals(obj);
  }

  @Nullable
  public static GrClosureSignature getLeastUpperBound(@NotNull GrClosureSignature signature1,
                                                      @NotNull GrClosureSignature signature2,
                                                      PsiManager manager) {
    GrClosureParameter[] parameters1 = signature1.getParameters();
    GrClosureParameter[] parameters2 = signature2.getParameters();

    if (parameters1.length == parameters2.length) {
      GrClosureParameter[] params = new GrClosureParameter[parameters1.length];
      for (int i = 0; i < params.length; i++) {
        final PsiType type = GenericsUtil.getGreatestLowerBound(parameters1[i].getType(), parameters2[i].getType());
        boolean opt = parameters1[i].isOptional() && parameters2[i].isOptional();
        params[i] = new GrClosureParameterImpl(type, opt, null);
      }
      final PsiType s1type = signature1.getReturnType();
      final PsiType s2type = signature2.getReturnType();
      PsiType returnType = null;
      if (s1type != null && s2type != null) {
        returnType = TypesUtil.getLeastUpperBound(s1type, s2type, manager);
      }
      boolean isVarArgs = signature1.isVarargs() && signature2.isVarargs();
      return new GrClosureSignatureImpl(params, returnType, isVarArgs);
    }
    return null; //todo
  }

  private class DerivedClosureSignature extends GrClosureSignatureImpl {
    DerivedClosureSignature() {
      super(GrClosureParameter.EMPTY_ARRAY, null);
    }

    DerivedClosureSignature(@NotNull GrClosureParameter[] params, @Nullable PsiType returnType, boolean isVarArgs) {
      super(params, returnType, isVarArgs);
    }

    @Override
    public PsiType getReturnType() {
      return GrClosureSignatureImpl.this.getReturnType();
    }
  }
}


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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author Maxim.Medvedev
 */
public class GrClosureSignatureImpl implements GrClosureSignature {
  private final boolean myIsVarargs;
  private final PsiType myReturnType;
  private final GrClosureParameter[] myParameters;

  public GrClosureSignatureImpl(PsiParameter[] parameters, PsiType returnType, PsiSubstitutor substitutor) {
    myReturnType = substitutor.substitute(returnType);
    final int length = parameters.length;
    myParameters = new GrClosureParameter[length];
    for (int i = 0; i < length; i++) {
      myParameters[i] = new GrClosureParameterImpl(parameters[i], substitutor);
    }
    if (length > 0) {
      myIsVarargs = parameters[length - 1].isVarArgs() || myParameters[length - 1].getType() instanceof PsiArrayType;
    }
    else {
      myIsVarargs = false;
    }
  }

  public GrClosureSignatureImpl(PsiParameter[] parameters, PsiType returnType) {
    this(parameters, returnType, PsiSubstitutor.EMPTY);
  }

  public GrClosureSignatureImpl(PsiParameter[] parameters) {
    this(parameters, null);
  }

  public GrClosureSignatureImpl(GrClosableBlock block) {
    this(block.getAllParameters(), block.getReturnType());
  }

  public GrClosureSignatureImpl(PsiMethod method) {
    this(method, PsiSubstitutor.EMPTY);
  }

  public GrClosureSignatureImpl(PsiMethod method, PsiSubstitutor substitutor) {
    this(method.getParameterList().getParameters(), method.getReturnType(), substitutor);
  }

  private GrClosureSignatureImpl(GrClosureParameter[] params, PsiType returnType, boolean isVarArgs) {
    myParameters = params;
    myReturnType = returnType;
    myIsVarargs = isVarArgs;
  }


  public boolean isVarargs() {
    return myIsVarargs;
  }


  public PsiType getReturnType() {
    return myReturnType;
  }

  @NotNull
  public GrClosureParameter[] getParameters() {
    GrClosureParameter[] result = new GrClosureParameter[myParameters.length];
    System.arraycopy(myParameters, 0, result, 0, myParameters.length);
    return result;
  }

  public GrClosureSignature curry(int count) {
    if (count > myParameters.length) {
      if (isVarargs()) {
        return new GrClosureSignatureImpl(GrClosureParameter.EMPTY_ARRAY, myReturnType);
      }
      else {
        return null;
      }
    }
    GrClosureParameter[] newParams = new GrClosureParameter[myParameters.length - count];
    System.arraycopy(myParameters, count, newParams, 0, newParams.length);
    return new GrClosureSignatureImpl(newParams, myReturnType, myIsVarargs);
  }

  public boolean isValid() {
    for (GrClosureParameter parameter : myParameters) {
      if (!parameter.isValid()) return false;
    }
    return myReturnType == null || myReturnType.isValid();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof GrClosureSignature) {
      return Comparing.equal(myParameters, ((GrClosureSignature)obj).getParameters()) &&
             Comparing.equal(myIsVarargs, ((GrClosureSignature)obj).isVarargs()) &&
             Comparing.equal(myReturnType, ((GrClosureSignature)obj).getReturnType());
    }
    return super.equals(obj);
  }

  @Nullable
  public static GrClosureSignature getLeastUpperBound(GrClosureSignature signature1, GrClosureSignature signature2, PsiManager manager) {
    GrClosureParameter[] parameters1 = signature1.getParameters();
    GrClosureParameter[] parameters2 = signature2.getParameters();

    if (parameters1.length == parameters2.length) {
      GrClosureParameter[] params = new GrClosureParameter[parameters1.length];
      for (int i = 0; i < params.length; i++) {
        final PsiType type = GenericsUtil.getGreatestLowerBound(parameters1[i].getType(), parameters2[i].getType());
        boolean opt = parameters1[i].isOptional() && parameters2[i].isOptional();
        params[i] = new GrClosureParameterImpl(/*null, */type, opt, null);
      }
      PsiType returnType = TypesUtil.getLeastUpperBound(signature1.getReturnType(), signature2.getReturnType(), manager);
      boolean isVarArgs = signature1.isVarargs() && signature2.isVarargs();
      return new GrClosureSignatureImpl(params, returnType, isVarArgs);
    }
    return null; //todo
  }
}


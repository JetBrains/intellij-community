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
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignatureVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author Maxim.Medvedev
 */
public class GrImmediateClosureSignatureImpl implements GrClosureSignature {
  private static final Logger LOG = Logger.getInstance(GrImmediateClosureSignatureImpl.class);

  private final boolean myIsVarargs;
  private final boolean myCurried;
  @Nullable private final PsiType myReturnType;
  @NotNull private final GrClosureParameter[] myParameters;
  @NotNull private final PsiSubstitutor mySubstitutor;

  public GrImmediateClosureSignatureImpl(@NotNull PsiParameter[] parameters,
                                         @Nullable PsiType returnType,
                                         @NotNull PsiSubstitutor substitutor) {
    LOG.assertTrue(returnType == null || returnType.isValid());
    LOG.assertTrue(substitutor.isValid());

    myReturnType = substitutor.substitute(returnType);
    final int length = parameters.length;
    myParameters = new GrClosureParameter[length];
    for (int i = 0; i < length; i++) {
      myParameters[i] = new GrImmediateClosureParameterImpl(parameters[i], substitutor);
    }
    myIsVarargs = GrClosureSignatureUtil.isVarArgsImpl(myParameters);
    mySubstitutor = substitutor;
    myCurried = false;
  }

  public GrImmediateClosureSignatureImpl(PsiParameter[] parameters, @Nullable PsiType returnType) {
    this(parameters, returnType, PsiSubstitutor.EMPTY);
  }

  public GrImmediateClosureSignatureImpl(@NotNull GrClosureParameter[] params, @Nullable PsiType returnType, boolean isVarArgs, boolean isCurried) {
    myParameters = params;
    myReturnType = returnType;
    myIsVarargs = isVarArgs;
    myCurried = isCurried;
    mySubstitutor = PsiSubstitutor.EMPTY;
  }

  @Override
  public boolean isVarargs() {
    return myIsVarargs;
  }


  @Override
  @Nullable
  public PsiType getReturnType() {
    return myReturnType;
  }

  @Override
  @NotNull
  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  @Override
  @NotNull
  public GrClosureParameter[] getParameters() {
    GrClosureParameter[] result = new GrClosureParameter[myParameters.length];
    System.arraycopy(myParameters, 0, result, 0, myParameters.length);
    return result;
  }

  @Override
  public int getParameterCount() {
    return myParameters.length;
  }

  @Override
  @Nullable
  public GrSignature curry(@NotNull PsiType[] args, int position, @NotNull PsiElement context) {
    return GrClosureSignatureUtil.curryImpl(this, args, position, context);
  }

  @Override
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

  @Override
  public boolean isCurried() {
    return myCurried;
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
        String name = StringUtil.equals(parameters1[i].getName(), parameters2[i].getName()) ? parameters1[i].getName() : null;
        params[i] = new GrImmediateClosureParameterImpl(type, name, opt, null);
      }
      final PsiType s1type = signature1.getReturnType();
      final PsiType s2type = signature2.getReturnType();
      PsiType returnType = null;
      if (s1type != null && s2type != null) {
        returnType = TypesUtil.getLeastUpperBound(s1type, s2type, manager);
      }
      boolean isVarArgs = signature1.isVarargs() && signature2.isVarargs();
      return new GrImmediateClosureSignatureImpl(params, returnType, isVarArgs, false);
    }
    return null; //todo
  }

  @Override
  public void accept(@NotNull GrSignatureVisitor visitor) {
    visitor.visitClosureSignature(this);
  }
}



// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.Arrays;

/**
 * @author Maxim.Medvedev
 */
public class GrImmediateClosureSignatureImpl implements GrSignature {

  private static final Logger LOG = Logger.getInstance(GrImmediateClosureSignatureImpl.class);

  private final boolean myIsVarargs;
  private final boolean myCurried;
  @Nullable private final PsiType myReturnType;
  private final GrClosureParameter @NotNull [] myParameters;
  @NotNull private final PsiSubstitutor mySubstitutor;

  public GrImmediateClosureSignatureImpl(PsiParameter @NotNull [] parameters,
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

  public GrImmediateClosureSignatureImpl(GrClosureParameter @NotNull [] params, @Nullable PsiType returnType, boolean isVarArgs, boolean isCurried) {
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
  public GrClosureParameter @NotNull [] getParameters() {
    return myParameters.clone();
  }

  @Override
  public int getParameterCount() {
    return myParameters.length;
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
    if (obj instanceof GrSignature) {
      return Arrays.equals(myParameters, ((GrSignature)obj).getParameters()) &&
             myIsVarargs == ((GrSignature)obj).isVarargs();
    }
    return super.equals(obj);
  }

  @Override
  public boolean isCurried() {
    return myCurried;
  }

  @Nullable
  public static GrSignature getLeastUpperBound(@NotNull GrSignature signature1,
                                               @NotNull GrSignature signature2,
                                               @NotNull PsiManager manager) {
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
}



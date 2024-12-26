// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;

/**
 * @author Maxim.Medvedev
 */
public class GrImmediateClosureParameterImpl implements GrClosureParameter {
  private static final Logger LOG = Logger.getInstance(GrImmediateClosureParameterImpl.class);

  private final PsiType myType;
  private final String myName;
  private final boolean myOptional;
  private final GrExpression myDefaultInitializer;

  public GrImmediateClosureParameterImpl(@Nullable PsiType type, @Nullable String name, boolean optional, @Nullable GrExpression defaultInitializer) {
    LOG.assertTrue(type == null || type.isValid());
    LOG.assertTrue(defaultInitializer == null || defaultInitializer.isValid());

    myType = type;
    myName = name;
    myOptional = optional;
    myDefaultInitializer = optional ? defaultInitializer : null;
  }

  public GrImmediateClosureParameterImpl(@NotNull PsiParameter parameter, @NotNull PsiSubstitutor substitutor) {
    this(substitutor.substitute(getParameterType(parameter)), getParameterName(parameter), isParameterOptional(parameter), getDefaultInitializer(parameter));
  }

  private static @Nullable PsiType getParameterType(@NotNull PsiParameter parameter) {
    return parameter instanceof GrParameter ? ((GrParameter)parameter).getDeclaredType() : parameter.getType();
  }

  public static @Nullable GrExpression getDefaultInitializer(PsiParameter parameter) {
    return parameter instanceof GrParameter ? ((GrParameter)parameter).getInitializerGroovy() : null;
  }

  public static boolean isParameterOptional(PsiParameter parameter) {
    return parameter instanceof GrParameter && ((GrParameter)parameter).isOptional();
  }

  public static @Nullable String getParameterName(@NotNull PsiParameter param) {
    if (param instanceof PsiCompiledElement) { // don't try to find out a compiled parameter name
      return null;
    }
    else {
      return param.getName();
    }
  }

  @Override
  public @Nullable PsiType getType() {
    return myType;
  }

  @Override
  public boolean isOptional() {
    return myOptional;
  }

  @Override
  public @Nullable GrExpression getDefaultInitializer() {
    return myDefaultInitializer;
  }

  @Override
  public boolean isValid() {
    return (myType == null || myType.isValid()) && (myDefaultInitializer == null || myDefaultInitializer.isValid());
  }

  @Override
  public @Nullable String getName() {
    return myName;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof GrClosureParameter) {
      return Comparing.equal(myType, ((GrClosureParameter)obj).getType()) &&
             Comparing.equal(myOptional, ((GrClosureParameter)obj).isOptional()) &&
             Comparing.equal(myDefaultInitializer, ((GrClosureParameter)obj).getDefaultInitializer());
    }
    return super.equals(obj);
  }
}

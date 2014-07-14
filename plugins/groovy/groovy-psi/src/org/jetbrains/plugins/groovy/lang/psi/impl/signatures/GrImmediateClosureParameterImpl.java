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

  @Nullable
  private static PsiType getParameterType(@NotNull PsiParameter parameter) {
    return parameter instanceof GrParameter ? ((GrParameter)parameter).getDeclaredType() : parameter.getType();
  }

  @Nullable
  public static GrExpression getDefaultInitializer(PsiParameter parameter) {
    return parameter instanceof GrParameter ? ((GrParameter)parameter).getInitializerGroovy() : null;
  }

  public static boolean isParameterOptional(PsiParameter parameter) {
    return parameter instanceof GrParameter && ((GrParameter)parameter).isOptional();
  }

  @Nullable
  public static String getParameterName(@NotNull PsiParameter param) {
    if (param instanceof PsiCompiledElement) { // don't try to find out a compiled parameter name
      return null;
    }
    else {
      return param.getName();
    }
  }

  @Override
  @Nullable
  public PsiType getType() {
    return myType;
  }

  @Override
  public boolean isOptional() {
    return myOptional;
  }

  @Override
  @Nullable
  public GrExpression getDefaultInitializer() {
    return myDefaultInitializer;
  }

  @Override
  public boolean isValid() {
    return (myType == null || myType.isValid()) && (myDefaultInitializer == null || myDefaultInitializer.isValid());
  }

  @Nullable
  @Override
  public String getName() {
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

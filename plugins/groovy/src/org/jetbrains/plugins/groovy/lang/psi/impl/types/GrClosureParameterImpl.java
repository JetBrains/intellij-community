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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;

/**
 * @author Maxim.Medvedev
 */
public class GrClosureParameterImpl implements GrClosureParameter {
  @Nullable final PsiType myType;
  @Nullable final String myName=null;
  final boolean myOptional;
  @Nullable final GrExpression myDefaultInitializer;

  public GrClosureParameterImpl(/*String name,*/ PsiType type, boolean optional, GrExpression defaultInitializer) {
//    myName = name;
    myType = type;
    myOptional = optional;
    if (myOptional) {
      myDefaultInitializer = defaultInitializer;
    }
    else {
      myDefaultInitializer = null;
    }
  }

  public GrClosureParameterImpl(@Nullable PsiType type) {
    this(/*null,*/ type, false, null);
  }

  public GrClosureParameterImpl(PsiParameter parameter) {
    this(parameter, PsiSubstitutor.EMPTY);
  }

  public GrClosureParameterImpl(PsiParameter parameter, PsiSubstitutor substitutor) {
    this(/*parameter.getName(), */substitutor.substitute(parameter.getType()),
         parameter instanceof GrParameter ? ((GrParameter)parameter).isOptional() : false,
         parameter instanceof GrParameter ? ((GrParameter)parameter).getDefaultInitializer() : null);
  }


  @Nullable
  public PsiType getType() {
    return myType;
  }

  public boolean isOptional() {
    return myOptional;
  }

  @Nullable
  public GrExpression getDefaultInitializer() {
    return myDefaultInitializer;
  }

  public boolean isValid() {
    return (myType == null || myType.isValid()) && (myDefaultInitializer == null || myDefaultInitializer.isValid());
  }

  @Nullable
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

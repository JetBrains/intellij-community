/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;

class GrClosureParameterImpl implements GrClosureParameter {

  private final PsiParameter myParameter;
  private final PsiSubstitutor mySubstitutor;
  private final boolean myEraseType;

  public GrClosureParameterImpl(@NotNull PsiParameter parameter) {
    this(parameter, PsiSubstitutor.EMPTY, false);
  }

  public GrClosureParameterImpl(@NotNull PsiParameter parameter, @NotNull PsiSubstitutor substitutor, boolean eraseType) {
    myParameter = parameter;
    mySubstitutor = substitutor;
    myEraseType = eraseType;
  }

  @Nullable
  @Override
  public PsiType getType() {
    PsiType type = mySubstitutor.substitute(myParameter.getType());
    return myEraseType ? TypeConversionUtil.erasure(type) : type;
  }

  @Override
  public boolean isOptional() {
    return myParameter instanceof GrParameter && ((GrParameter)myParameter).isOptional();
  }

  @Nullable
  @Override
  public GrExpression getDefaultInitializer() {
    return myParameter instanceof GrParameter ? ((GrParameter)myParameter).getInitializerGroovy() : null;
  }

  @Override
  public boolean isValid() {
    return myParameter.isValid();
  }

  @Nullable
  @Override
  public String getName() {
    return myParameter.getName();
  }
}

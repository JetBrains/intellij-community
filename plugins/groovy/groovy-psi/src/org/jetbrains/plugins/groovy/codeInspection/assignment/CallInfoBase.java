/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * Created by Max Medvedev on 05/02/14
 */
public abstract class CallInfoBase<T extends GrCall> implements CallInfo<T> {
  private final T myCall;
  private final NullableLazyValue<PsiType[]> myArgTypes = new NullableLazyValue<PsiType[]>() {
    @Nullable
    @Override
    protected PsiType[] compute() {
      return inferArgTypes();
    }
  };

  protected CallInfoBase(T call) {
    myCall = call;
  }

  @Nullable
  protected abstract PsiType[] inferArgTypes();

  @Nullable
  @Override
  public GrArgumentList getArgumentList() {
    return myCall.getArgumentList();
  }

  @Nullable
  @Override
  public PsiType[] getArgumentTypes() {
    return myArgTypes.getValue();
  }

  @NotNull
  @Override
  public GroovyResolveResult advancedResolve() {
    return myCall.advancedResolve();
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve() {
    return myCall.multiResolve(false);
  }

  @NotNull
  @Override
  public T getCall() {
    return myCall;
  }

  @Override
  @NotNull
  public GrExpression[] getExpressionArguments() {
    return myCall.getExpressionArguments();
  }

  @Override
  @NotNull
  public GrClosableBlock[] getClosureArguments() {
    return myCall.getClosureArguments();
  }

  @Override
  @NotNull
  public GrNamedArgument[] getNamedArguments() {
    return myCall.getNamedArguments();
  }
}

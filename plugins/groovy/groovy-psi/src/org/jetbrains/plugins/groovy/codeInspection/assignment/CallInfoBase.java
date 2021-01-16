// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;

public abstract class CallInfoBase<T extends GrCall> implements CallInfo<T> {
  private final T myCall;
  private final NullableLazyValue<PsiType[]> myArgTypes = new NullableLazyValue<>() {
    @Override
    protected PsiType @Nullable [] compute() {
      return inferArgTypes();
    }
  };

  protected CallInfoBase(T call) {
    myCall = call;
  }

  protected abstract PsiType @Nullable [] inferArgTypes();

  @Nullable
  @Override
  public GrArgumentList getArgumentList() {
    return myCall.getArgumentList();
  }

  @Override
  public PsiType @Nullable [] getArgumentTypes() {
    return myArgTypes.getValue();
  }

  @NotNull
  @Override
  public T getCall() {
    return myCall;
  }
}

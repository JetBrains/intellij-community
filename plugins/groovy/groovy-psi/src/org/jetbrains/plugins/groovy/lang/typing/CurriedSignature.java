// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.typing;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping;
import org.jetbrains.plugins.groovy.lang.resolve.api.CallParameter;
import org.jetbrains.plugins.groovy.lang.resolve.api.CallSignature;

import java.util.Collections;
import java.util.List;

class CurriedSignature implements CallSignature<CallParameter> {

  private final CallSignature<?> myOriginal;
  private final int myPosition;
  private final List<? extends Argument> myArguments;

  CurriedSignature(@NotNull CallSignature<?> original, int position, @NotNull List<? extends Argument> arguments) {
    myOriginal = original;
    myPosition = position;
    myArguments = arguments;
  }

  @Override
  public boolean isVararg() {
    return myOriginal.isVararg();
  }

  @Override
  public @Nullable PsiType getReturnType() {
    return myOriginal.getReturnType();
  }

  @Override
  public int getParameterCount() {
    return myOriginal.getParameterCount() - myArguments.size();
  }

  @Override
  public @NotNull List<CallParameter> getParameters() {
    final int argumentCount = myArguments.size();
    final List<? extends CallParameter> originalParameters = myOriginal.getParameters();
    final int originalParameterCount = originalParameters.size();
    if (isVararg()) {
      final CallParameter varargParameter = ContainerUtil.getLastItem(originalParameters);
      final List<? extends CallParameter> nonVarargParameters = originalParameters.subList(0, originalParameterCount - 1);
      return ContainerUtil.concat(
        nonVarargParameters.subList(0, myPosition),
        nonVarargParameters.subList(myPosition + argumentCount, nonVarargParameters.size() - 1),
        Collections.singletonList(varargParameter)
      );
    }
    else {
      return ContainerUtil.concat(
        originalParameters.subList(0, myPosition),
        originalParameters.subList(myPosition + argumentCount, originalParameterCount)
      );
    }
  }

  @Override
  public @Nullable ArgumentMapping<? extends CallParameter> applyTo(@NotNull List<? extends Argument> arguments, @NotNull PsiElement context) {
    final int argumentCount = arguments.size();
    final int position = myPosition < 0
                         ? isVararg()
                           ? myPosition + argumentCount + myArguments.size()
                           : myPosition + getParameterCount()
                         : myPosition;
    if (position < 0 || argumentCount < position) {
      return null;
    }
    final List<? extends Argument> uncurriedArguments = ContainerUtil.concat(
      arguments.subList(0, position),
      myArguments,
      arguments.subList(position, argumentCount)
    );
    return myOriginal.applyTo(uncurriedArguments, context);
  }
}

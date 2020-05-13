// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import kotlin.Lazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.api.CallSignature;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.mapNotNull;
import static org.jetbrains.plugins.groovy.util.FunctionsKt.recursionAwareLazy;

/**
 * @see org.codehaus.groovy.runtime.CurriedClosure
 */
final class GroovyCurriedClosureType extends GroovyClosureType {

  private final GroovyClosureType myOriginal;
  private final int myPosition;
  private final List<? extends Argument> myArguments;
  private final int myCurriedArgumentCount;
  private final Lazy<List<CallSignature<?>>> mySignatures;

  GroovyCurriedClosureType(@NotNull GroovyClosureType original,
                           int position,
                           @NotNull List<? extends Argument> arguments,
                           int curriedArgumentCount,
                           @NotNull PsiElement context) {
    super(context);
    myOriginal = original;
    myPosition = position;
    myArguments = arguments;
    myCurriedArgumentCount = curriedArgumentCount;
    mySignatures = recursionAwareLazy(() -> mapNotNull(myOriginal.getSignatures(), this::curry));
  }

  @Override
  public boolean isValid() {
    return super.isValid() && myOriginal.isValid();
  }

  @NotNull
  @Override
  public List<CallSignature<?>> getSignatures() {
    return mySignatures.getValue();
  }

  @Nullable
  private CallSignature<?> curry(CallSignature<?> original) {
    if (original.isVararg()) {
      return new CurriedSignature(original, myPosition, myArguments);
    }
    else {
      final int position = myPosition < 0 ? myPosition + original.getParameterCount() - myCurriedArgumentCount
                                          : myPosition;
      if (position < 0) {
        return null;
      }
      return new CurriedSignature(original, position, myArguments);
    }
  }

  @NotNull
  @Override
  public PsiType curry(int position, @NotNull List<? extends Argument> arguments, @NotNull PsiElement context) {
    return new GroovyCurriedClosureType(this, position, arguments, myCurriedArgumentCount + myArguments.size(), context);
  }
}

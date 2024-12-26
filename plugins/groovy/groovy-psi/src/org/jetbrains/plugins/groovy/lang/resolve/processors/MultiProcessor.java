// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static java.util.Collections.singletonList;

@ApiStatus.Internal
public interface MultiProcessor extends PsiScopeProcessor {

  @NotNull
  Iterable<? extends PsiScopeProcessor> getProcessors();

  static @NotNull Iterable<? extends PsiScopeProcessor> allProcessors(@NotNull PsiScopeProcessor processor) {
    return processor instanceof MultiProcessor ? ((MultiProcessor)processor).getProcessors()
                                               : singletonList(processor);
  }
}

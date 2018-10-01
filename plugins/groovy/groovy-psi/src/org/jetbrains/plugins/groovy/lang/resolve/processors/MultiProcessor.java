// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static java.util.Collections.singletonList;
import static org.jetbrains.annotations.ApiStatus.Experimental;

@Experimental
public interface MultiProcessor extends PsiScopeProcessor {

  @NotNull
  Collection<? extends PsiScopeProcessor> getProcessors();

  @NotNull
  static Iterable<? extends PsiScopeProcessor> allProcessors(@NotNull PsiScopeProcessor processor) {
    return processor instanceof MultiProcessor ? ((MultiProcessor)processor).getProcessors()
                                               : singletonList(processor);
  }
}

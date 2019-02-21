// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface HighlightSink {

  default void registerProblem(@NotNull PsiElement highlightElement, @NotNull String message, @NotNull LocalQuickFix... fixes) {
    registerProblem(highlightElement, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, message, fixes);
  }

  default void registerError(@NotNull PsiElement highlightElement, @NotNull String message, @NotNull LocalQuickFix... fixes) {
    registerProblem(highlightElement, ProblemHighlightType.GENERIC_ERROR, message, fixes);
  }

  void registerProblem(@NotNull PsiElement highlightElement,
                       @NotNull ProblemHighlightType highlightType,
                       @NotNull String message,
                       @NotNull LocalQuickFix... fixes);
}

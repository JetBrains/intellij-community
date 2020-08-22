// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.highlighting;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil.fixesToIntentions;
import static org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil.intentionsToFixes;

public interface HighlightSink {

  default void registerProblem(@NotNull PsiElement highlightElement,
                               @NotNull @InspectionMessage String message,
                               LocalQuickFix @NotNull ... fixes) {
    registerProblem(highlightElement, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, message, fixes);
  }

  default void registerError(@NotNull PsiElement highlightElement,
                             @NotNull @InspectionMessage String message,
                             LocalQuickFix @NotNull ... fixes) {
    registerProblem(highlightElement, ProblemHighlightType.GENERIC_ERROR, message, fixes);
  }

  default void registerProblem(@NotNull PsiElement highlightElement,
                               @NotNull ProblemHighlightType highlightType,
                               @NotNull @InspectionMessage String message,
                               LocalQuickFix @NotNull ... fixes) {
    registerProblem(highlightElement, highlightType, message, fixesToIntentions(highlightElement, fixes));
  }

  default void registerProblem(@NotNull PsiElement highlightElement,
                               @NotNull ProblemHighlightType highlightType,
                               @NotNull @InspectionMessage String message,
                               @NotNull List<? extends IntentionAction> actions) {
    registerProblem(highlightElement, highlightType, message, intentionsToFixes(highlightElement, actions));
  }
}

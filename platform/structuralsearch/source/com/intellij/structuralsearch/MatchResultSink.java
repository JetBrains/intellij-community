// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for consumers of match results
 */
public interface MatchResultSink {
  /**
   * Notifies sink about new match
   */
  void newMatch(@NotNull MatchResult result);

  /**
   *  Notifies sink about starting the matching for given element
   * @param element the current file
   */
  void processFile(@NotNull PsiFile element);

  /**
   * Sets the reference to the matching process
   * @param matchingProcess the matching process reference
   */
  void setMatchingProcess(@NotNull MatchingProcess matchingProcess);

  /**
   * Notifies sink about end of matching.
   */
  void matchingFinished();

  @Nullable
  ProgressIndicator getProgressIndicator();
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.MatchResultSink;
import com.intellij.structuralsearch.MatchingProcess;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public final class DuplicateFilteringResultSink implements MatchResultSink {
  private final MatchResultSink delegate;
  private final Set<SmartPsiPointer> duplicates = new HashSet<>();

  public DuplicateFilteringResultSink(@NotNull MatchResultSink delegate) {
    this.delegate = delegate;
  }

  @Override
  public void newMatch(@NotNull MatchResult result) {
    if (!duplicates.add(result.getMatchRef())) {
      return;
    }
    delegate.newMatch(result);
  }

  @Override
  public void processFile(@NotNull PsiFile element) {
    delegate.processFile(element);
  }

  @Override
  public void setMatchingProcess(@NotNull MatchingProcess matchingProcess) {
    delegate.setMatchingProcess(matchingProcess);
  }

  @Override
  public void matchingFinished() {
    duplicates.clear();
    delegate.matchingFinished();
  }

  @Override
  public ProgressIndicator getProgressIndicator() {
    return delegate.getProgressIndicator();
  }
}

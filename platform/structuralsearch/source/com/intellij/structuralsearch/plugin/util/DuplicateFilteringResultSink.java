package com.intellij.structuralsearch.plugin.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.MatchResultSink;
import com.intellij.structuralsearch.MatchingProcess;
import gnu.trove.THashSet;

import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public class DuplicateFilteringResultSink implements MatchResultSink {
  private final MatchResultSink delegate;
  final Set<SmartPsiPointer> duplicates = new THashSet<>();

  public DuplicateFilteringResultSink(MatchResultSink delegate) {
    this.delegate = delegate;
  }

  @Override
  public void newMatch(MatchResult result) {
    if (!duplicates.add(result.getMatchRef())) {
      return;
    }
    delegate.newMatch(result);
  }

  @Override
  public void processFile(PsiFile element) {
    delegate.processFile(element);
  }

  @Override
  public void setMatchingProcess(MatchingProcess matchingProcess) {
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

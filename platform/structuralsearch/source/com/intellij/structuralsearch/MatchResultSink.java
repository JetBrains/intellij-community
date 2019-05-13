package com.intellij.structuralsearch;

import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.MatchingProcess;
import com.intellij.openapi.progress.ProgressIndicator;

/**
 * Interface for consumers of match results
 */
public interface MatchResultSink {
  /**
   * Notifies sink about new match
   * @param result
   */
  void newMatch(MatchResult result);

  /**
   *  Notifies sink about starting the matching for given element
   * @param element the current file
   */
  void processFile(PsiFile element);

  /**
   * Sets the reference to the matching process
   * @param matchingProcess the matching process reference
   */
  void setMatchingProcess(MatchingProcess matchingProcess);

  /**
   * Notifies sink about end of matching.
   */
  void matchingFinished();

  ProgressIndicator getProgressIndicator();
}

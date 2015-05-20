package com.intellij.structuralsearch.plugin.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.MatchResultSink;
import com.intellij.structuralsearch.MatchingProcess;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CollectingMatchResultSink implements MatchResultSink {
  private final List<MatchResult> matches = new ArrayList<MatchResult>();

  public void newMatch(MatchResult result) {
    matches.add(result);
  }

  /* Notifies sink about starting the matching for given element
   * @param element the current file
   */
  public void processFile(PsiFile element) {
  }

  public void matchingFinished() {
  }

  public ProgressIndicator getProgressIndicator() {
    return null;
  }

  public void setMatchingProcess(MatchingProcess process) {
  }

  @NotNull
  public List<MatchResult> getMatches() {
    return matches;
  }
}

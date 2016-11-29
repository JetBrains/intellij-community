package com.intellij.structuralsearch.plugin.util;

import com.intellij.structuralsearch.DefaultMatchResultSink;
import com.intellij.structuralsearch.MatchResult;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CollectingMatchResultSink extends DefaultMatchResultSink {
  private final List<MatchResult> matches = new ArrayList<>();

  @Override
  public void newMatch(MatchResult result) {
    matches.add(result);
  }

  @NotNull
  public List<MatchResult> getMatches() {
    return matches;
  }
}

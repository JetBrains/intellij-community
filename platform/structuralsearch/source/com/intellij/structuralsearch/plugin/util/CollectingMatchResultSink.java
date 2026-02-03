// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.util;

import com.intellij.structuralsearch.DefaultMatchResultSink;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CollectingMatchResultSink extends DefaultMatchResultSink {
  private final List<MatchResult> matches = new SmartList<>();

  @Override
  public void newMatch(@NotNull MatchResult result) {
    matches.add(result);
  }

  public @NotNull List<MatchResult> getMatches() {
    return matches;
  }
}

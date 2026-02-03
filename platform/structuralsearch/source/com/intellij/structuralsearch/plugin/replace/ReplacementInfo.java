// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.replace;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ReplacementInfo {
  String getReplacement();

  @Nullable
  PsiElement getMatch(int index);

  int getMatchesCount();

  MatchResult getNamedMatchResult(@NotNull String name);

  @NotNull
  MatchResult getMatchResult();

  String getVariableName(@NotNull PsiElement element);

  String getSearchPatternName(@NotNull String sourceName);
}

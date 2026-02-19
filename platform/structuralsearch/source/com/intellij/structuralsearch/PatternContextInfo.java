// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PatternContextInfo {
  private final @NotNull PatternTreeContext myTreeContext;
  private final @Nullable PatternContext myPatternContext;
  private final @Nullable String myContextConstraint;

  public PatternContextInfo(@NotNull PatternTreeContext treeContext,
                            @Nullable PatternContext patternContext,
                            @Nullable String contextConstraint) {
    myTreeContext = treeContext;
    myPatternContext = patternContext;
    myContextConstraint = contextConstraint;
  }

  public PatternContextInfo(@NotNull PatternTreeContext treeContext,
                            @Nullable PatternContext patternContext) {
    this(treeContext, patternContext, null);
  }

  public PatternContextInfo(@NotNull PatternTreeContext treeContext) {
    this(treeContext, null, null);
  }

  public @NotNull PatternTreeContext getTreeContext() {
    return myTreeContext;
  }

  public @Nullable PatternContext getPatternContext() {
    return myPatternContext;
  }

  public @Nullable String getContextConstraint() {
    return myContextConstraint;
  }
}

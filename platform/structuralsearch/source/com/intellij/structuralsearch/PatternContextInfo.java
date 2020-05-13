// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PatternContextInfo {
  @NotNull private final PatternTreeContext myTreeContext;
  @Nullable private final PatternContext myPatternContext;
  @Nullable private final String myContextConstraint;

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

  @NotNull
  public PatternTreeContext getTreeContext() {
    return myTreeContext;
  }

  @Nullable
  public PatternContext getPatternContext() {
    return myPatternContext;
  }

  @Nullable
  public String getContextConstraint() {
    return myContextConstraint;
  }
}

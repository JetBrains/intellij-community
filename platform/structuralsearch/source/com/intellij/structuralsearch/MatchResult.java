// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public abstract class MatchResult {
  @NonNls public static final String LINE_MATCH = "__line__";
  @NonNls public static final String MULTI_LINE_MATCH = "__multi_line__";

  public abstract String getMatchImage();

  public abstract SmartPsiPointer getMatchRef();
  public abstract PsiElement getMatch();
  public abstract int getStart();
  public abstract int getEnd();

  public abstract String getName();

  public abstract List<MatchResult> getChildren();
  public abstract boolean hasChildren();
  public abstract int size();

  public abstract boolean isScopeMatch();
  public abstract boolean isMultipleMatch();

  public abstract MatchResult getRoot();
  public abstract boolean isTarget();
}

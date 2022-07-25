// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class MatchResult {
  @NonNls public static final String LINE_MATCH = "__line__";
  @NonNls public static final String MULTI_LINE_MATCH = "__multi_line__";

  public abstract String getMatchImage();

  public abstract SmartPsiElementPointer<?> getMatchRef();
  public abstract PsiElement getMatch();
  public abstract int getStart();
  public abstract int getEnd();

  public abstract String getName();

  @NotNull
  public abstract List<MatchResult> getChildren();
  public abstract boolean hasChildren();
  public abstract int size();

  public abstract boolean isScopeMatch();
  public abstract boolean isMultipleMatch();

  @NotNull
  public abstract MatchResult getRoot();
  public abstract boolean isTarget();
}

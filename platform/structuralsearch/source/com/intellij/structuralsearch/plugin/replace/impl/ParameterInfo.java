// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class ParameterInfo extends UserDataHolderBase {
  private final @NotNull String name;
  private final int startIndex;
  private final boolean replacementVariable;
  private boolean argumentContext;
  private boolean statementContext;
  private int afterDelimiterPos;
  private boolean hasCommaBefore;
  private int beforeDelimiterPos;
  private boolean hasCommaAfter;
  private PsiElement myElement;

  public ParameterInfo(@NotNull String name, int startIndex, boolean replacementVariable) {
    this.name = name;
    this.startIndex = startIndex;
    this.replacementVariable = replacementVariable;
  }

  public @NotNull String getName() {
    return name;
  }

  public int getStartIndex() {
    return startIndex;
  }

  public boolean isArgumentContext() {
    return argumentContext;
  }

  public void setArgumentContext(boolean argumentContext) {
    this.argumentContext = argumentContext;
  }

  public boolean isStatementContext() {
    return statementContext;
  }

  public void setStatementContext(boolean statementContext) {
    this.statementContext = statementContext;
  }

  public int getAfterDelimiterPos() {
    return afterDelimiterPos;
  }

  public void setAfterDelimiterPos(int afterDelimiterPos) {
    this.afterDelimiterPos = afterDelimiterPos;
  }

  public boolean isHasCommaBefore() {
    return hasCommaBefore;
  }

  public void setHasCommaBefore(boolean hasCommaBefore) {
    this.hasCommaBefore = hasCommaBefore;
  }

  public int getBeforeDelimiterPos() {
    return beforeDelimiterPos;
  }

  public void setBeforeDelimiterPos(int beforeDelimiterPos) {
    this.beforeDelimiterPos = beforeDelimiterPos;
  }

  public boolean isHasCommaAfter() {
    return hasCommaAfter;
  }

  public void setHasCommaAfter(boolean hasCommaAfter) {
    this.hasCommaAfter = hasCommaAfter;
  }

  public boolean isReplacementVariable() {
    return replacementVariable;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public void setElement(@NotNull PsiElement element) {
    myElement = element;
  }
}

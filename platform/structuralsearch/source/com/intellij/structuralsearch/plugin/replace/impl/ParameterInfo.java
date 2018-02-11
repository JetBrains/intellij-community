// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.psi.PsiElement;

public final class ParameterInfo {
  private final String name;
  private final int startIndex;
  private final boolean replacementVariable;
  private boolean argumentContext;
  private boolean methodParameterContext;
  private boolean statementContext;
  private int afterDelimiterPos;
  private boolean hasCommaBefore;
  private int beforeDelimiterPos;
  private boolean hasCommaAfter;
  private PsiElement myElement;

  public ParameterInfo(String name, int startIndex, boolean replacementVariable) {
    this.name = name;
    this.startIndex = startIndex;
    this.replacementVariable = replacementVariable;
  }

  public String getName() {
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

  public boolean isMethodParameterContext() {
    return methodParameterContext;
  }

  public void setMethodParameterContext(boolean methodParameterContext) {
    this.methodParameterContext = methodParameterContext;
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

  public void setElement(PsiElement element) {
    myElement = element;
  }
}

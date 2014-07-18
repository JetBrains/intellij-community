package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.psi.PsiElement;

public final class ParameterInfo {
  private String name;
  private int startIndex;
  private boolean parameterContext;
  private boolean methodParameterContext;
  private boolean statementContext;
  private boolean variableInitialContext;
  private int afterDelimiterPos;
  private boolean hasCommaBefore;
  private int beforeDelimiterPos;
  private boolean hasCommaAfter;
  private boolean scopeParameterization;
  private boolean replacementVariable;
  private PsiElement myElement;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getStartIndex() {
    return startIndex;
  }

  public void setStartIndex(int startIndex) {
    this.startIndex = startIndex;
  }

  public boolean isParameterContext() {
    return parameterContext;
  }

  public void setParameterContext(boolean parameterContext) {
    this.parameterContext = parameterContext;
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

  public boolean isVariableInitialContext() {
    return variableInitialContext;
  }

  public void setVariableInitialContext(boolean variableInitialContext) {
    this.variableInitialContext = variableInitialContext;
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

  public boolean isScopeParameterization() {
    return scopeParameterization;
  }

  public void setScopeParameterization(boolean scopeParameterization) {
    this.scopeParameterization = scopeParameterization;
  }

  public boolean isReplacementVariable() {
    return replacementVariable;
  }

  public void setReplacementVariable(boolean replacementVariable) {
    this.replacementVariable = replacementVariable;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public void setElement(PsiElement element) {
    myElement = element;
  }
}

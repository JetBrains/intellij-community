package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.psi.PsiElement;

public final class ParameterInfo {
  private String name;
  private int startIndex;
  private boolean argumentContext;
  private boolean methodParameterContext;
  private boolean statementContext;
  private boolean variableInitializerContext;
  private int afterDelimiterPos;
  private boolean hasCommaBefore;
  private int beforeDelimiterPos;
  private boolean hasCommaAfter;
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

  public boolean isVariableInitializerContext() {
    return variableInitializerContext;
  }

  public void setVariableInitializerContext(boolean variableInitializerContext) {
    this.variableInitializerContext = variableInitializerContext;
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

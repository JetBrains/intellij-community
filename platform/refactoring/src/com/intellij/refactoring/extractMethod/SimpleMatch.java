// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class SimpleMatch {
  PsiElement myStartElement;
  PsiElement myEndElement;
  private final Map<String, String> myChangedParameters;
  private String myChangedOutput;

  public SimpleMatch(final @NotNull PsiElement start, final @NotNull PsiElement endElement) {
    myStartElement = start;
    myEndElement = endElement;
    myChangedParameters = new HashMap<>();
  }

  public PsiElement getStartElement() {
    return myStartElement;
  }

  public PsiElement getEndElement() {
    return myEndElement;
  }

  public Map<String, String> getChangedParameters() {
    return myChangedParameters;
  }

  public void changeParameter(final @NotNull String from, final @NotNull String to) {
    myChangedParameters.put(from, to);
  }

  public void changeOutput(final @NotNull String to) {
    myChangedOutput = to;
  }

  public String getChangedOutput() {
    return myChangedOutput;
  }

}

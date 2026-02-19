// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.parameterInfo;

import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class MockCreateParameterInfoContext implements CreateParameterInfoContext {
  private Object[] myItems;
  private PsiElement myHighlightedElement;
  private final Editor myEditor;
  private final PsiFile myFile;

  public MockCreateParameterInfoContext(@NotNull Editor editor, @NotNull PsiFile file) {
    myEditor = editor;
    myFile = file;
  }

  @Override
  public Object[] getItemsToShow() {
    return myItems;
  }

  @Override
  public void setItemsToShow(Object[] items) {
    myItems = items;
  }

  @Override
  public void showHint(PsiElement element, int offset, ParameterInfoHandler handler) {}

  @Override
  public int getParameterListStart() {
    return myEditor.getCaretModel().getOffset();
  }

  @Override
  public PsiElement getHighlightedElement() {
    return myHighlightedElement;
  }

  @Override
  public void setHighlightedElement(PsiElement elements) {
    myHighlightedElement = elements;
  }

  @Override
  public Project getProject() {
    return myFile.getProject();
  }

  @Override
  public PsiFile getFile() {
    return myFile;
  }

  @Override
  public int getOffset() {
    return myEditor.getCaretModel().getOffset();
  }

  @Override
  public @NotNull Editor getEditor() {
    return myEditor;
  }
}

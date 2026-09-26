// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.parameterInfo;

import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author gregsh
*/
public class MockUpdateParameterInfoContext implements UpdateParameterInfoContext {
  private final Editor myEditor;
  private final PsiFile myFile;
  private PsiElement myParameterOwner;
  private Object myHighlightedParameter;
  private int myCurrentParameter;
  private final Object[] myItems;
  private final boolean[] myCompEnabled;

  public MockUpdateParameterInfoContext(@NotNull Editor editor, @NotNull PsiFile file) {
    this(editor, file, null);
  }

  public MockUpdateParameterInfoContext(@NotNull Editor editor, @NotNull PsiFile file, Object @Nullable [] items) {
    myEditor = editor;
    myFile = file;
    myItems = items == null ? ArrayUtilRt.EMPTY_OBJECT_ARRAY : items;
    myCompEnabled = items == null ? null : new boolean[items.length];
  }

  @Override
  public void removeHint() {}

  @Override
  public void setParameterOwner(PsiElement o) {
    myParameterOwner = o;
  }

  @Override
  public PsiElement getParameterOwner() { return myParameterOwner; }

  @Override
  public void setHighlightedParameter(Object parameter) {
    myHighlightedParameter = parameter;
  }

  @Override
  public Object getHighlightedParameter() {
    return myHighlightedParameter;
  }

  @Override
  public void setCurrentParameter(int index) {
    myCurrentParameter = index;
  }

  public int getCurrentParameter() {
    return myCurrentParameter;
  }

  @Override
  public boolean isUIComponentEnabled(int index) {
    return myCompEnabled != null && myCompEnabled[index];
  }

  @Override
  public void setUIComponentEnabled(int index, boolean b) {
    if (myCompEnabled != null) {
      myCompEnabled[index] = b;
    }
  }

  @Override
  public int getParameterListStart() {
    return myEditor.getCaretModel().getOffset();
  }

  @Override
  public Object[] getObjectsToView() {
    return myItems;
  }

  @Override
  public boolean isPreservedOnHintHidden() {
    return false;
  }

  @Override
  public void setPreservedOnHintHidden(boolean value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isInnermostContext() {
    return false;
  }

  @Override
  public boolean isSingleParameterInfo() {
    return false;
  }

  @Override
  public UserDataHolderEx getCustomContext() {
    throw new UnsupportedOperationException();
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

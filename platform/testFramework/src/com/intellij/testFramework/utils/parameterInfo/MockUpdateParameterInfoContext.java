// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.utils.parameterInfo;

import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
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

  public MockUpdateParameterInfoContext(@NotNull Editor editor, @NotNull PsiFile file, @Nullable Object[] items) {
    myEditor = editor;
    myFile = file;
    myItems = items == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : items;
    myCompEnabled = items == null ? null : new boolean[items.length];
  }

  public void removeHint() {}

  public void setParameterOwner(PsiElement o) {
    myParameterOwner = o;
  }

  public PsiElement getParameterOwner() { return myParameterOwner; }

  public void setHighlightedParameter(Object parameter) {
    myHighlightedParameter = parameter;
  }

  public Object getHighlightedParameter() {
    return myHighlightedParameter;
  }

  public void setCurrentParameter(int index) {
    myCurrentParameter = index;
  }

  public int getCurrentParameter() {
    return myCurrentParameter;
  }

  public boolean isUIComponentEnabled(int index) {
    return myCompEnabled != null && myCompEnabled[index];
  }

  public void setUIComponentEnabled(int index, boolean b) {
    if (myCompEnabled != null) {
      myCompEnabled[index] = b;
    }
  }

  public int getParameterListStart() {
    return myEditor.getCaretModel().getOffset();
  }

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
  public UserDataHolderEx getCustomContext() {
    throw new UnsupportedOperationException();
  }

  public Project getProject() {
    return myFile.getProject();
  }

  public PsiFile getFile() {
    return myFile;
  }

  public int getOffset() {
    return myEditor.getCaretModel().getOffset();
  }

  @NotNull
  public Editor getEditor() {
    return myEditor;
  }
}

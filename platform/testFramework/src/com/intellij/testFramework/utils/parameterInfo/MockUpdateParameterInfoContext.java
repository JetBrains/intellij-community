/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework.utils.parameterInfo;

import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
* @author gregsh
*/
public class MockUpdateParameterInfoContext implements UpdateParameterInfoContext {
  private final Editor myEditor;
  private final PsiFile myFile;
  private PsiElement myParameterOwner;
  private Object myHighlightedParameter;
  private int myCurrentParameter;

  public MockUpdateParameterInfoContext(@NotNull Editor editor, @NotNull PsiFile file) {
    myEditor = editor;
    myFile = file;
  }

  public void removeHint() {}

  public void setParameterOwner(PsiElement o) {
    myParameterOwner = o;
  }

  public PsiElement getParameterOwner() { return myParameterOwner; }

  public void setHighlightedParameter(Object parameter) {
    myHighlightedParameter = parameter;
  }

  public void setCurrentParameter(int index) {
    myCurrentParameter = index;
  }

  public int getCurrentParameter() {
    return myCurrentParameter;
  }

  public boolean isUIComponentEnabled(int index) { return false; }

  public void setUIComponentEnabled(int index, boolean b) {}

  public int getParameterListStart() {
    return myEditor.getCaretModel().getOffset();
  }

  public Object[] getObjectsToView() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
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

/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testFramework.propertyBased;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiFileRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
abstract class ActionOnRange implements MadTestingAction {
  private final SmartPsiFileRange myMarker;
  private TextRange myFinalRange;
  protected final int myInitialStart; 
  protected final int myInitialEnd; 

  ActionOnRange(PsiFile file, int start, int end) {
    myInitialStart = start;
    myInitialEnd = end;
    myMarker = SmartPointerManager.getInstance(file.getProject()).createSmartPsiFileRangePointer(file, new ProperTextRange(start, end));
    int length = file.getTextLength();
    assert length == getDocument().getTextLength() : file + " " + getDocument();
    assert end <= length : end + " >= " + length;
  }

  @NotNull
  PsiFile getFile() {
    return PsiManager.getInstance(getProject()).findFile(getVirtualFile());
  }

  @NotNull
  Project getProject() {
    return myMarker.getProject();
  }

  @NotNull
  VirtualFile getVirtualFile() {
    return myMarker.getVirtualFile();
  }

  @NotNull
  Document getDocument() {
    return FileDocumentManager.getInstance().getDocument(getVirtualFile());
  }

  Segment getCurrentRange() {
    return myFinalRange != null ? myFinalRange : myMarker.getRange();
  }
  
  int getCurrentStartOffset() {
    return getStartOffset(getCurrentRange());
  }

  int getFinalStartOffset() {
    return getStartOffset(getFinalRange());
  }

  private static int getStartOffset(Segment range) {
    return range == null ? -1 : range.getStartOffset();
  }

  @Nullable
  TextRange getFinalRange() {
    if (myFinalRange == null) {
      Segment range = myMarker.getRange();
      myFinalRange = range != null ? TextRange.create(range) : null;
    }
    return myFinalRange;
  }
}

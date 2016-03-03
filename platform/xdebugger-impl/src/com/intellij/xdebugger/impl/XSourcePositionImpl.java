/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class XSourcePositionImpl implements XSourcePosition {
  private final VirtualFile myFile;
  private final int myLine;
  private final int myOffset;

  private XSourcePositionImpl(@NotNull VirtualFile file, final int line, final int offset) {
    myFile = file;
    myLine = line;
    myOffset = offset;
  }

  @Override
  public int getLine() {
    return myLine;
  }

  @Override
  public int getOffset() {
    return myOffset;
  }

  @Override
  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  /**
   * do not call this method from plugins, use {@link XDebuggerUtil#createPositionByOffset(VirtualFile, int)} instead
   */
  @Nullable
  public static XSourcePositionImpl createByOffset(@Nullable VirtualFile file, final int offset) {
    if (file == null) return null;

    AccessToken lock = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      Document document = FileDocumentManager.getInstance().getDocument(file);

      if (document == null) {
        return null;
      }
      int line = offset <= document.getTextLength() ? document.getLineNumber(offset) : -1;
      return new XSourcePositionImpl(file, line, offset);
    }
    finally {
      lock.finish();
    }
  }

  @Nullable
  public static XSourcePositionImpl createByElement(@Nullable PsiElement element) {
    if (element == null) return null;

    VirtualFile file = element.getContainingFile().getVirtualFile();

    if (file == null) return null;

    return createByOffset(file, element.getTextOffset());
  }

  /**
   * do not call this method from plugins, use {@link XDebuggerUtil#createPosition(VirtualFile, int)} instead
   */
  @Nullable
  public static XSourcePositionImpl create(@Nullable VirtualFile file, int line) {
    return create(file, line, 0);
  }

  /**
   * do not call this method from plugins, use {@link XDebuggerUtil#createPosition(VirtualFile, int, int)} instead
   */
  @Nullable
  public static XSourcePositionImpl create(@Nullable VirtualFile file, int line, int column) {
    if (file == null) {
      return null;
    }

    AccessToken lock = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      int offset;
      if (file instanceof LightVirtualFile || file instanceof HttpVirtualFile) {
        offset = -1;
      }
      else {

        Document document = file.isValid() ? FileDocumentManager.getInstance().getDocument(file) : null;
        if (document == null) {
          return null;
        }
        if (line < 0) {
          line = 0;
        }
        if (column < 0) {
          column = 0;
        }

        offset = line < document.getLineCount() ? document.getLineStartOffset(line) + column : -1;

        if (offset >= document.getTextLength()) {
          offset = document.getTextLength() - 1;
        }
      }
      return new XSourcePositionImpl(file, line, offset);
    }
    finally {
      lock.finish();
    }
  }

  @Override
  @NotNull
  public Navigatable createNavigatable(@NotNull Project project) {
    return doCreateOpenFileDescriptor(project, this);
  }

  @NotNull
  public static OpenFileDescriptor createOpenFileDescriptor(@NotNull Project project, @NotNull XSourcePosition position) {
    Navigatable navigatable = position.createNavigatable(project);
    if (navigatable instanceof OpenFileDescriptor) {
      return (OpenFileDescriptor)navigatable;
    }
    else {
      return doCreateOpenFileDescriptor(project, position);
    }
  }

  @NotNull
  public static OpenFileDescriptor doCreateOpenFileDescriptor(@NotNull Project project, @NotNull XSourcePosition position) {
    return position.getOffset() != -1
           ? new OpenFileDescriptor(project, position.getFile(), position.getOffset())
           : new OpenFileDescriptor(project, position.getFile(), position.getLine(), 0);
  }

  @Override
  public String toString() {
    return "XSourcePositionImpl[" + myFile + ":" + myLine + "(" + myOffset + ")]";
  }
}

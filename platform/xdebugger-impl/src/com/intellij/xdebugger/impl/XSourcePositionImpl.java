/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
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
   * do not call this method from plugins, use {@link com.intellij.xdebugger.XDebuggerUtil#createPositionByOffset(com.intellij.openapi.vfs.VirtualFile, int)} instead
   */
  @Nullable
  public static XSourcePositionImpl createByOffset(@Nullable VirtualFile file, final int offset) {
    if (file == null) return null;
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) {
      return null;
    }
    int line = offset < document.getTextLength() ? document.getLineNumber(offset) : -1;
    return new XSourcePositionImpl(file, line, offset);
  }

  /**
   * do not call this method from plugins, use {@link com.intellij.xdebugger.XDebuggerUtil#createPosition(com.intellij.openapi.vfs.VirtualFile, int)} instead
   */
  @Nullable
  public static XSourcePositionImpl create(@Nullable VirtualFile file, int line) {
    if (file == null) return null;

    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) {
      return null;
    }
    if (line < 0){
      line = 0;
    }
    int offset = line < document.getLineCount() ? document.getLineStartOffset(line) : -1;
    return new XSourcePositionImpl(file, line, offset);
  }

  @Override
  @NotNull
  public Navigatable createNavigatable(@NotNull Project project) {
    return createOpenFileDescriptor(project, this);
  }

  @NotNull
  public static OpenFileDescriptor createOpenFileDescriptor(@NotNull Project project, @NotNull XSourcePosition position) {
    return position.getOffset() != -1 ? new OpenFileDescriptor(project, position.getFile(), position.getOffset()) : new OpenFileDescriptor(project, position.getFile(), position.getLine(), 0);
  }
}

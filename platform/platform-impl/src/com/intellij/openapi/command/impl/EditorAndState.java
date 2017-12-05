// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Objects;

class EditorAndState {
  private final FileEditorState myState;
  private final VirtualFile myVirtualFile;

  public EditorAndState(FileEditor editor, FileEditorState state) {
    myVirtualFile = editor.getFile();
    myState = state;
  }

  public boolean canBeAppliedTo(FileEditor editor) {
    if (editor == null) return false;
    if (!Objects.equals(myVirtualFile, editor.getFile())) return false;
    if (myState == null) return false;
    FileEditorState currentState = editor.getState(FileEditorStateLevel.UNDO);
    return myState.getClass() == currentState.getClass();
  }

  public FileEditorState getState() {
    return myState;
  }
}

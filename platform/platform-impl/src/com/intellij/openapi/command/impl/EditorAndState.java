// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class EditorAndState {

  static @Nullable EditorAndState getStateFor(@Nullable Project project, @NotNull CurrentEditorProvider editorProvider) {
    FileEditor editor = editorProvider.getCurrentEditor(project);
    if (editor == null) {
      return null;
    }
    if (!editor.isValid()) {
      return null;
    }
    return new EditorAndState(editor, editor.getState(FileEditorStateLevel.UNDO));
  }

  private final FileEditorState myState;
  private final VirtualFile myVirtualFile;

  EditorAndState(FileEditor editor, FileEditorState state) {
    myVirtualFile = editor.getFile();
    myState = state;
  }

  boolean canBeAppliedTo(FileEditor editor) {
    if (editor == null) return false;
    if (!Objects.equals(myVirtualFile, editor.getFile())) return false;
    if (myState == null) return false;
    FileEditorState currentState = editor.getState(FileEditorStateLevel.UNDO);
    return myState.getClass() == currentState.getClass();
  }

  FileEditorState getState() {
    return myState;
  }
}

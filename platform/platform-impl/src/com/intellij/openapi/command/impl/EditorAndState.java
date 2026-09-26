// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    if (editor != null && editor.isValid()) {
      FileEditorState state = editor.getState(FileEditorStateLevel.UNDO);
      return new EditorAndState(editor, state);
    }
    return null;
  }

  private final @NotNull FileEditorState editorState;
  private final VirtualFile virtualFile;

  EditorAndState(@NotNull FileEditor editor, @NotNull FileEditorState state) {
    virtualFile = editor.getFile();
    editorState = state;
  }

  boolean canBeAppliedTo(@Nullable FileEditor editor) {
    if (editor == null || !Objects.equals(virtualFile, editor.getFile())) {
      return false;
    }
    FileEditorState currentState = editor.getState(FileEditorStateLevel.UNDO);
    return editorState.getClass() == currentState.getClass();
  }

  @NotNull FileEditorState getState() {
    return editorState;
  }

  @Override
  public String toString() {
    return editorState.toString();
  }
}

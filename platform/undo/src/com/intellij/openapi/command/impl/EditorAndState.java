// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
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
  // An undo stack of another document can hold this state, so a strong reference would keep the file of a closed editor.
  private final @Nullable Reference<VirtualFile> virtualFile;

  EditorAndState(@NotNull FileEditor editor, @NotNull FileEditorState state) {
    VirtualFile file = editor.getFile();
    virtualFile = file == null ? null : new WeakReference<>(file);
    editorState = state;
  }

  boolean canBeAppliedTo(@Nullable FileEditor editor) {
    if (editor == null) {
      return false;
    }
    VirtualFile file = virtualFile == null ? null : virtualFile.get();
    // A collected file has no editor.
    if ((virtualFile != null && file == null) || !Objects.equals(file, editor.getFile())) {
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

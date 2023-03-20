// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.DocumentsEditor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;

public class PlatformUndoTest extends LightPlatformTestCase {
  public void testIncorrectFileEditorDoesNotCauseHanging() {
    LightVirtualFile file = new LightVirtualFile(getTestName(false));
    Document d1 = EditorFactory.getInstance().createDocument("");
    Document d2 = EditorFactory.getInstance().createDocument("");
    FileEditor fileEditor = new IncorrectFileEditor(file, d1, d2);
    runWithCurrentEditor(fileEditor, () -> {
      WriteAction.run(() -> {
        CommandProcessor.getInstance().runUndoTransparentAction(() -> d1.insertString(0, " "));
        CommandProcessor.getInstance().runUndoTransparentAction(() -> d2.insertString(0, " "));
      });
      undo();
    });
    assertEquals("", d1.getText());
    assertEquals("", d2.getText());
  }

  private void undo() {
    UndoManagerImpl undoManager = getUndoManager();
    FileEditor fileEditor = undoManager.getEditorProvider().getCurrentEditor();
    assertTrue(undoManager.isUndoAvailable(fileEditor));
    undoManager.undo(fileEditor);
  }

  private void runWithCurrentEditor(FileEditor currentEditor, Runnable task) {
    UndoManagerImpl undoManager = getUndoManager();
    CurrentEditorProvider savedProvider = undoManager.getEditorProvider();
    try {
      undoManager.setEditorProvider(() -> currentEditor);
      task.run();
    }
    finally {
      undoManager.setEditorProvider(savedProvider);
    }
  }

  private UndoManagerImpl getUndoManager() {
    return (UndoManagerImpl)UndoManager.getInstance(getProject());
  }

  private static final class IncorrectFileEditor extends UserDataHolderBase implements DocumentsEditor {
    private final JComponent myComponent = new JPanel();
    private final VirtualFile myFile;
    private final Document[] myDocuments;

    private IncorrectFileEditor(VirtualFile file, @NotNull Document @NotNull ... documents) {
      myFile = file;
      myDocuments = documents;
    }

    @Override
    public @NotNull VirtualFile getFile() {
      return myFile;
    }
    @Override
    public @NotNull Document @NotNull [] getDocuments() {
      return myDocuments;
    }

    @Override
    public @NotNull JComponent getComponent() {
      return myComponent;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
      return null;
    }

    @Override
    public @NotNull String getName() {
      return "testEditor";
    }

    @Override
    public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
      return new State();
    }

    @Override
    public void setState(@NotNull FileEditorState state) {}

    @Override
    public boolean isModified() {
      return false;
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {}

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {}

    @Override
    public void dispose() {}

    private static class State implements FileEditorState {
      @Override
      public boolean canBeMergedWith(@NotNull FileEditorState otherState, @NotNull FileEditorStateLevel level) {
        return false;
      }
    }
  }
}

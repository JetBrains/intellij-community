// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeListener;

public class LargeFileEditorProvider extends TextEditorProvider {

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return TextEditorProvider.isTextFile(file)
           && SingleRootFileViewProvider.isTooLargeForContentLoading(file)
           && (file.getFileType().isBinary() || !Experiments.isFeatureEnabled("new.large.text.file.viewer"));
  }

  @Override
  @NotNull
  public FileEditor createEditor(@NotNull Project project, @NotNull final VirtualFile file) {
    return file.getFileType().isBinary() ?
           new LargeBinaryFileEditor(file) :
           new LargeTextFileEditor(project, file, this);
  }

  @Override
  @NotNull
  public String getEditorTypeId() {
    return "LargeFileEditor";
  }

  public static class LargeTextFileEditor extends TextEditorImpl {
    LargeTextFileEditor(@NotNull Project project,
                        @NotNull VirtualFile file,
                        @NotNull TextEditorProvider provider) {
      super(project, file, provider);
      ObjectUtils.consumeIfCast(getEditor(), EditorEx.class, editorEx -> editorEx.setViewer(true));
    }
  }

  private static class LargeBinaryFileEditor extends UserDataHolderBase implements FileEditor {
    private final VirtualFile myFile;

    LargeBinaryFileEditor(VirtualFile file) {
      myFile = file;
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      JLabel label = new JLabel(
        "Binary file " + myFile.getPath() + " is too large (" + StringUtil.formatFileSize(myFile.getLength()) + ")");
      label.setHorizontalAlignment(SwingConstants.CENTER);
      return label;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return null;
    }

    @NotNull
    @Override
    public String getName() {
      return "Large file editor";
    }

    @NotNull
    @Override
    public FileEditorState getState(@NotNull FileEditorStateLevel level) {
      return new TextEditorState();
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
    }

    @Override
    public boolean isModified() {
      return false;
    }

    @Override
    public boolean isValid() {
      return myFile.isValid();
    }

    @Override
    public void selectNotify() {
    }

    @Override
    public void deselectNotify() {
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public BackgroundEditorHighlighter getBackgroundHighlighter() {
      return null;
    }

    @Override
    public FileEditorLocation getCurrentLocation() {
      return null;
    }

    @Override
    public StructureViewBuilder getStructureViewBuilder() {
      return null;
    }

    @Override
    public void dispose() {
    }
  }
}

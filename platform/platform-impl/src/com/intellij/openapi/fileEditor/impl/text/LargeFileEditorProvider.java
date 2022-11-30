// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditor;
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

public final class LargeFileEditorProvider extends TextEditorProvider {
  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return TextEditorProvider.isTextFile(file)
           && SingleRootFileViewProvider.isTooLargeForContentLoading(file)
           && !(Experiments.getInstance().isFeatureEnabled("new.large.text.file.viewer")
                && !file.getFileType().isBinary()
                && file.isInLocalFileSystem());
  }

  @Override
  public @NotNull FileEditor createEditor(@NotNull Project project, final @NotNull VirtualFile file) {
    return file.getFileType().isBinary() ? new LargeBinaryFileEditor(file) : new LargeTextFileEditor(project, file, this);
  }

  @Override
  public @NotNull String getEditorTypeId() {
    return "LargeFileEditor";
  }

  public static class LargeTextFileEditor extends TextEditorImpl {
    LargeTextFileEditor(@NotNull Project project, @NotNull VirtualFile file, @NotNull TextEditorProvider provider) {
      super(project, file, provider);
      ObjectUtils.consumeIfCast(getEditor(), EditorEx.class, editorEx -> editorEx.setViewer(true));
    }
  }

  private static class LargeBinaryFileEditor extends UserDataHolderBase implements FileEditor {
    private final VirtualFile myFile;

    LargeBinaryFileEditor(VirtualFile file) {
      myFile = file;
    }

    @Override
    public @NotNull JComponent getComponent() {
      JLabel label = new JLabel(IdeBundle.message("binary.file.too.large", myFile.getPath(), StringUtil.formatFileSize(myFile.getLength())));
      label.setHorizontalAlignment(SwingConstants.CENTER);
      return label;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return null;
    }

    @Override
    public @NotNull String getName() {
      return IdeBundle.message("large.file.editor.name");
    }

    @Override
    public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
      return new TextEditorState();
    }

    @Override
    public void setState(@NotNull FileEditorState state) { }

    @Override
    public boolean isModified() {
      return false;
    }

    @Override
    public boolean isValid() {
      return myFile.isValid();
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { }

    @Override
    public @NotNull VirtualFile getFile() {
      return myFile;
    }

    @Override
    public void dispose() { }
  }
}

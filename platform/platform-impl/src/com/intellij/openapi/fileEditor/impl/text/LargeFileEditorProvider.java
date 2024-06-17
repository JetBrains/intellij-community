// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SingleRootFileViewProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeListener;

import static com.intellij.openapi.fileEditor.impl.text.TextEditorImplKt.createAsyncEditorLoader;
import static com.intellij.openapi.fileEditor.impl.text.TextEditorImplKt.createEditorImpl;

public final class LargeFileEditorProvider extends TextEditorProvider {
  static final Key<Boolean> IS_LARGE = Key.create("IS_LARGE");

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return isTextFile(file)
           && SingleRootFileViewProvider.isTooLargeForContentLoading(file)
           && !(Experiments.getInstance().isFeatureEnabled("new.large.text.file.viewer")
                && !file.getFileType().isBinary()
                && file.isInLocalFileSystem());
  }

  @Override
  public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    if (file.getFileType().isBinary()) {
      return new LargeBinaryFileEditor(file);
    }
    else {
      AsyncEditorLoader asyncLoader = createAsyncEditorLoader(this, project, file, null);
      EditorImpl editor = createEditorImpl(project, file, asyncLoader).getFirst();
      editor.setViewer(true);

      TextEditorImpl testEditor = new TextEditorImpl(project, file, new TextEditorComponent(file, editor), asyncLoader, true);
      testEditor.putUserData(IS_LARGE, true);
      return testEditor;
    }
  }

  @Override
  public @NotNull String getEditorTypeId() {
    return "LargeFileEditor";
  }

  private static final class LargeBinaryFileEditor extends UserDataHolderBase implements FileEditor {
    private final VirtualFile file;

    LargeBinaryFileEditor(VirtualFile file) {
      this.file = file;
    }

    @Override
    public @NotNull JComponent getComponent() {
      JLabel label = new JLabel(IdeBundle.message("binary.file.too.large", file.getPath(), StringUtil.formatFileSize(file.getLength())));
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
      return file.isValid();
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { }

    @Override
    public @NotNull VirtualFile getFile() {
      return file;
    }

    @Override
    public void dispose() { }
  }
}

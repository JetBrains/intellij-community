/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.*;
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
    return TextEditorProvider.isTextFile(file) && SingleRootFileViewProvider.isTooLargeForContentLoading(file);
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

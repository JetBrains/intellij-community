// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl.http;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.impl.text.TextEditorState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
class HttpFileEditorProvider implements FileEditorProvider, DumbAware {
  @Override
  public boolean accept(@NotNull final Project project, @NotNull final VirtualFile file) {
    return file instanceof HttpVirtualFile && !file.isDirectory();
  }

  @Override
  @NotNull
  public FileEditor createEditor(@NotNull final Project project, @NotNull final VirtualFile file) {
    return new HttpFileEditor(project, (HttpVirtualFile)file);
  }

  @Override
  @NotNull
  public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    return new TextEditorState();
  }

  @Override
  @NotNull
  public String getEditorTypeId() {
    return "httpFileEditor";
  }

  @Override
  @NotNull
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}

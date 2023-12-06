// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

final class HttpFileEditorProvider implements FileEditorProvider, DumbAware {
  @Override
  public boolean accept(final @NotNull Project project, final @NotNull VirtualFile file) {
    return file instanceof HttpVirtualFile && !file.isDirectory();
  }

  @Override
  public boolean acceptRequiresReadAction() {
    return false;
  }

  @Override
  public @NotNull FileEditor createEditor(final @NotNull Project project, final @NotNull VirtualFile file) {
    return new HttpFileEditor(project, (HttpVirtualFile)file);
  }

  @Override
  public @NotNull FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    return new TextEditorState();
  }

  @Override
  public @NotNull String getEditorTypeId() {
    return "httpFileEditor";
  }

  @Override
  public @NotNull FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}

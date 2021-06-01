// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.testAssistant.vfs.TestDataGroupVirtualFile;


public class TestDataGroupEditorProvider implements FileEditorProvider, DumbAware {
  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return file instanceof TestDataGroupVirtualFile;
  }

  @Override
  @NotNull
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new TestDataGroupFileEditor(project, (TestDataGroupVirtualFile) file);
  }

  @Override
  @NotNull
  public String getEditorTypeId() {
    return "TestDataGroup";
  }

  @Override
  @NotNull
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}

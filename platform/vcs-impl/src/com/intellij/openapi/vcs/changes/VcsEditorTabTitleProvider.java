// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.diff.editor.DiffRequestProcessorEditor;
import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsEditorTabTitleProvider implements EditorTabTitleProvider, DumbAware {

  @Nullable
  @Override
  public String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
    return getEditorTabName(project, file);
  }

  @Nullable
  @Override
  public String getEditorTabTooltipText(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    return getEditorTabName(project, virtualFile);
  }

  @Nullable
  @NlsContexts.TabTitle
  private static String getEditorTabName(@NotNull Project project, @NotNull VirtualFile file) {
    if (file instanceof PreviewDiffVirtualFile) {
      FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(file);
      DiffRequestProcessorEditor editor = ContainerUtil.findInstance(editors, DiffRequestProcessorEditor.class);
      DiffRequestProcessor processor = editor != null ? editor.getProcessor() : null;
      return ((PreviewDiffVirtualFile)file).getProvider().getEditorTabName(processor);
    }

    return null;
  }
}

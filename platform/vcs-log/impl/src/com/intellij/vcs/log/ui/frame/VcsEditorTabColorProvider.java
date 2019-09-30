package com.intellij.vcs.log.ui.frame;

import com.intellij.diff.editor.DiffVirtualFile;
import com.intellij.diff.editor.GraphViewVirtualFile;
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.tabs.FileColorManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class VcsEditorTabColorProvider implements EditorTabColorProvider {

  @Nullable
  @Override
  public Color getEditorTabColor(@NotNull Project project, @NotNull VirtualFile file) {
    if (file instanceof GraphViewVirtualFile) {
      return FileColorManagerImpl.getInstance(project).getColor("Violet");
    }

    if (file instanceof DiffVirtualFile) {
      return FileColorManagerImpl.getInstance(project).getColor("Green");
    }

    return null;
  }
}

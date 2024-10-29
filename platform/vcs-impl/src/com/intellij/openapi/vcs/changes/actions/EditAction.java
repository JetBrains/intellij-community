// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class EditAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    List<VirtualFile> files = e.getData(VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY);
    assert project != null;
    assert files != null;
    editFilesAndShowErrors(project, files);
  }

  public static void editFilesAndShowErrors(@NotNull Project project, @NotNull List<? extends VirtualFile> files) {
    List<VcsException> exceptions = new ArrayList<>();
    editFiles(project, files, exceptions);
    if (!exceptions.isEmpty()) {
      AbstractVcsHelper.getInstance(project).showErrors(exceptions, VcsBundle.message("edit.errors"));
    }
  }

  public static void editFiles(@NotNull Project project, @NotNull List<? extends VirtualFile> files, List<? super VcsException> exceptions) {
    ChangesUtil.processVirtualFilesByVcs(project, files, (vcs, items) -> {
      EditFileProvider provider = vcs.getEditFileProvider();
      if (provider == null) {
        return;
      }

      try {
        provider.editFiles(VfsUtilCore.toVirtualFileArray(items));
      }
      catch (VcsException e1) {
        exceptions.add(e1);
      }

      VcsDirtyScopeManager vcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
      FileStatusManager fileStatusManager = FileStatusManager.getInstance(project);
      for (VirtualFile file : items) {
        vcsDirtyScopeManager.fileDirty(file);
        fileStatusManager.fileStatusChanged(file);
      }
    });
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    List<VirtualFile> files = e.getData(VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY);
    boolean enabled = files != null && !files.isEmpty();
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
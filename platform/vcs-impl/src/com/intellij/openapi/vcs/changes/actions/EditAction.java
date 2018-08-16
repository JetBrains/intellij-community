// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class EditAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    List<VirtualFile> files = e.getData(VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY);
    editFilesAndShowErrors(project, files);
  }

  public static void editFilesAndShowErrors(Project project, List<VirtualFile> files) {
    final List<VcsException> exceptions = new ArrayList<>();
    editFiles(project, files, exceptions);
    if (!exceptions.isEmpty()) {
      AbstractVcsHelper.getInstance(project).showErrors(exceptions, VcsBundle.message("edit.errors"));
    }
  }

  public static void editFiles(final Project project, final List<VirtualFile> files, final List<VcsException> exceptions) {
    ChangesUtil.processVirtualFilesByVcs(project, files, (vcs, items) -> {
      final EditFileProvider provider = vcs.getEditFileProvider();
      if (provider != null) {
        try {
          provider.editFiles(VfsUtil.toVirtualFileArray(items));
        }
        catch (VcsException e1) {
          exceptions.add(e1);
        }
        for(VirtualFile file: items) {
          VcsDirtyScopeManager.getInstance(project).fileDirty(file);
          FileStatusManager.getInstance(project).fileStatusChanged(file);
        }
      }
    });
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    List<VirtualFile> files = e.getData(VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY);
    boolean enabled = files != null && !files.isEmpty();
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }
}
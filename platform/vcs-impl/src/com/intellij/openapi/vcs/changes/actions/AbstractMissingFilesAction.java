// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ChangesViewManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractMissingFilesAction extends AnAction implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    List<FilePath> files = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    boolean enabled = files != null && !files.isEmpty();
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final List<FilePath> files = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    if (files == null) return;

    final ProgressManager progressManager = ProgressManager.getInstance();
    final Runnable action = () -> {
      final List<VcsException> allExceptions = new ArrayList<>();
      ChangesUtil.processFilePathsByVcs(project, files, (vcs, items) -> {
        final List<VcsException> exceptions = processFiles(vcs, files);
        if (exceptions != null) {
          allExceptions.addAll(exceptions);
        }
      });

      for (FilePath file : files) {
        VcsDirtyScopeManager.getInstance(project).fileDirty(file);
      }
      ChangesViewManager.getInstance(project).scheduleRefresh();
      if (allExceptions.size() > 0) {
        AbstractVcsHelper.getInstance(project).showErrors(allExceptions, "VCS Errors");
      }
    };
    if (synchronously()) {
      action.run();
    } else {
      progressManager.runProcessWithProgressSynchronously(action, getName(), true, project);
    }
  }

  protected abstract boolean synchronously();
  protected abstract String getName();

  protected abstract List<VcsException> processFiles(final AbstractVcs vcs, final List<FilePath> files);
}
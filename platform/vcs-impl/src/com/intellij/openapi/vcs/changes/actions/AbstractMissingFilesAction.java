/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:09:59
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ChangesViewManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractMissingFilesAction extends AnAction implements DumbAware {

  public void update(AnActionEvent e) {
    List<FilePath> files = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    boolean enabled = files != null && !files.isEmpty();
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final List<FilePath> files = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    if (files == null) return;

    final ProgressManager progressManager = ProgressManager.getInstance();
    final Runnable action = new Runnable() {
      public void run() {
        final List<VcsException> allExceptions = new ArrayList<>();
        ChangesUtil.processFilePathsByVcs(project, files, new ChangesUtil.PerVcsProcessor<FilePath>() {
          public void process(final AbstractVcs vcs, final List<FilePath> items) {
            final List<VcsException> exceptions = processFiles(vcs, files);
            if (exceptions != null) {
              allExceptions.addAll(exceptions);
            }
          }
        });

        for (FilePath file : files) {
          VcsDirtyScopeManager.getInstance(project).fileDirty(file);
        }
        ChangesViewManager.getInstance(project).scheduleRefresh();
        if (allExceptions.size() > 0) {
          AbstractVcsHelper.getInstance(project).showErrors(allExceptions, "VCS Errors");
        }
      }
    };
    if (synchronously()) {
      action.run();
    } else {
      progressManager.runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND, action);
        }
      }, getName(), true, project);
    }
  }

  protected abstract boolean synchronously();
  protected abstract String getName();

  protected abstract List<VcsException> processFiles(final AbstractVcs vcs, final List<FilePath> files);
}
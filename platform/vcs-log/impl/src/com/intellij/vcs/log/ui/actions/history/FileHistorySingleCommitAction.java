/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.actions.history;

import com.google.common.primitives.Ints;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.data.DataGetter;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.history.FileHistoryUi;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public abstract class FileHistorySingleCommitAction<T extends VcsCommitMetadata> extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    FileHistoryUi ui = e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI);
    if (project == null || ui == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setVisible(true);

    List<T> details = getSelection(ui);
    if (details.isEmpty()) {
      e.getPresentation().setEnabled(false);
      return;
    }

    T detail = getFirstItem(details);
    if (detail instanceof LoadingDetails) detail = null;
    e.getPresentation().setEnabled(details.size() == 1 && isEnabled(ui, detail, e));
  }

  protected boolean isEnabled(@NotNull FileHistoryUi ui, @Nullable T detail, @NotNull AnActionEvent e) {
    return true;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this);

    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    FileHistoryUi ui = e.getRequiredData(VcsLogInternalDataKeys.FILE_HISTORY_UI);

    List<CommitId> commits = ui.getVcsLog().getSelectedCommits();
    if (commits.size() != 1) return;
    CommitId commit = notNull(getFirstItem(commits));

    List<Integer> commitIndex = Ints.asList(ui.getLogData().getCommitIndex(commit.getHash(), commit.getRoot()));
    getDetailsGetter(ui).loadCommitsData(commitIndex, details -> {
      if (!details.isEmpty()) {
        performAction(project, ui, notNull(getFirstItem(details)), e);
      }
    }, t -> VcsBalloonProblemNotifier.showOverChangesView(project, "Could not load selected commits: " + t.getMessage(),
                                                          MessageType.ERROR), null);
  }

  @NotNull
  protected abstract List<T> getSelection(@NotNull FileHistoryUi ui);

  @NotNull
  protected abstract DataGetter<T> getDetailsGetter(@NotNull FileHistoryUi ui);

  protected abstract void performAction(@NotNull Project project,
                                        @NotNull FileHistoryUi ui,
                                        @NotNull T detail,
                                        @NotNull AnActionEvent e);
}

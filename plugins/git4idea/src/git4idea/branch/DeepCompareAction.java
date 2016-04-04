/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.branch;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogBranchFilterImpl;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.filter.BranchPopupBuilder;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Set;

public class DeepCompareAction extends ToggleAction implements DumbAware {

  @Override
  public boolean isSelected(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VcsLogUi ui = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    if (project == null || ui == null) {
      return false;
    }
    return DeepComparator.getInstance(project, ui).hasHighlightingOrInProgress();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean selected) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    final VcsLogUi ui = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    final VcsLogDataProvider dataProvider = e.getData(VcsLogDataKeys.VCS_LOG_DATA_PROVIDER);
    if (project == null || ui == null || dataProvider == null) {
      return;
    }
    final DeepComparator dc = DeepComparator.getInstance(project, ui);
    if (selected) {
      VcsLogBranchFilter branchFilter = ui.getFilterUi().getFilters().getBranchFilter();
      String singleBranchName = branchFilter != null ? VcsLogUtil.getSingleFilteredBranch(branchFilter, ui.getDataPack().getRefs()) : null;
      if (singleBranchName == null) {
        selectBranchAndPerformAction(ui.getDataPack(), e, new Consumer<String>() {
          @Override
          public void consume(String selectedBranch) {
            ui.getFilterUi().setFilter(VcsLogBranchFilterImpl.fromBranch(selectedBranch));
            dc.highlightInBackground(selectedBranch, dataProvider);
          }
        }, getAllVisibleRoots(ui));
        return;
      }
      dc.highlightInBackground(singleBranchName, dataProvider);
    }
    else {
      dc.stopAndUnhighlight();
    }
  }

  private static void selectBranchAndPerformAction(@NotNull VcsLogDataPack dataPack,
                                                   @NotNull AnActionEvent event,
                                                   @NotNull final Consumer<String> consumer,
                                                   @NotNull Collection<VirtualFile> visibleRoots) {
    ActionGroup actionGroup = new BranchPopupBuilder(dataPack, visibleRoots, null) {
      @NotNull
      @Override
      protected AnAction createAction(@NotNull final String name) {
        return new DumbAwareAction(name) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            consumer.consume(name);
          }
        };
      }
    }.build();
    ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup("Select branch to compare", actionGroup, event.getDataContext(), false, false, false, null, -1, null);
    InputEvent inputEvent = event.getInputEvent();
    if (inputEvent instanceof MouseEvent) {
      popup.show(new RelativePoint((MouseEvent)inputEvent));
    }
    else {
      popup.showInBestPositionFor(event.getDataContext());
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getData(CommonDataKeys.PROJECT);
    VcsLogUi ui = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    e.getPresentation().setEnabledAndVisible(project != null && ui != null &&
                                             hasGitRoots(project, getAllVisibleRoots(ui)));
  }

  private static boolean hasGitRoots(@NotNull Project project, @NotNull Set<VirtualFile> roots) {
    final GitRepositoryManager manager = ServiceManager.getService(project, GitRepositoryManager.class);
    return ContainerUtil.exists(roots, new Condition<VirtualFile>() {
      @Override
      public boolean value(VirtualFile root) {
        return manager.getRepositoryForRoot(root) != null;
      }
    });
  }

  @NotNull
  private static Set<VirtualFile> getAllVisibleRoots(@NotNull VcsLogUi ui) {
    return VcsLogUtil.getAllVisibleRoots(ui.getDataPack().getLogProviders().keySet(), ui.getFilterUi().getFilters().getRootFilter(),
                                         ui.getFilterUi().getFilters().getStructureFilter());
  }
}

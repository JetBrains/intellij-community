/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.Future;

public class GoToHashOrRefAction extends DumbAwareAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    final VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
    final VcsLogUiImpl logUi = (VcsLogUiImpl)e.getData(VcsLogDataKeys.VCS_LOG_UI);
    if (project == null || log == null || logUi == null) {
      return;
    }

    Set<VirtualFile> visibleRoots = VcsLogUtil.getVisibleRoots(logUi);
    Collection<VcsRef> visibleBranches = VcsLogUtil.getVisibleBranches(log, visibleRoots);
    GoToHashOrRefPopup popup = new GoToHashOrRefPopup(project, visibleBranches, visibleRoots, new Function<String, Future>() {
      @Override
      public Future fun(String text) {
        return log.jumpToReference(text);
      }
    }, new Function<VcsRef, Future>() {
      @Override
      public Future fun(VcsRef vcsRef) {
        return logUi.jumpToCommit(vcsRef.getCommitHash(), vcsRef.getRoot());
      }
    }, logUi.getColorManager(), new VcsRefComparator(logUi.getDataPack().getLogProviders()));
    popup.show(logUi.getTable());
  }

  @Override
  public void update(AnActionEvent e) {
    VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
    VcsLogUi logUi = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    e.getPresentation().setEnabledAndVisible(e.getProject() != null && log != null && logUi != null);
  }

  private static class VcsRefComparator implements Comparator<VcsRef> {
    @NotNull private final Map<VirtualFile, VcsLogProvider> myProviders;

    public VcsRefComparator(@NotNull Map<VirtualFile, VcsLogProvider> providers) {
      myProviders = providers;
    }

    @Override
    public int compare(@NotNull VcsRef ref1, @NotNull VcsRef ref2) {
      VcsLogProvider provider1 = myProviders.get(ref1.getRoot());
      VcsLogProvider provider2 = myProviders.get(ref1.getRoot());

      if (provider1 == null) return provider2 == null ? ref1.getName().compareTo(ref2.getName()) : 1;
      if (provider2 == null) return -1;

      if (provider1 == provider2) {
        return provider1.getReferenceManager().getLabelsOrderComparator().compare(ref1, ref2);
      }

      return provider1.getSupportedVcs().getName().compareTo(provider2.getSupportedVcs().getName());
    }
  }
}

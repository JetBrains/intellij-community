// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @deprecated Use {@link CommittedChangesBrowser}
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
public class RepositoryChangesBrowser extends ChangesBrowser {
  public RepositoryChangesBrowser(final Project project, final List<? extends CommittedChangeList> changeLists) {
    this(project, changeLists, null, null);
  }

  public RepositoryChangesBrowser(final Project project, final List<? extends ChangeList> changeLists, final List<Change> changes,
                                  final ChangeList initialListSelection) {
    super(project, null, Collections.emptyList(), initialListSelection, false, false, null, MyUseCase.COMMITTED_CHANGES, null);
  }

  @Override
  protected void buildToolBar(final DefaultActionGroup toolBarGroup) {
    super.buildToolBar(toolBarGroup);

    toolBarGroup.add(ActionManager.getInstance().getAction("Vcs.RepositoryChangesBrowserToolbar"));
  }

  @Override
  public Object getData(@NotNull @NonNls final String dataId) {
    if (VcsDataKeys.SELECTED_CHANGES.is(dataId)) {
      final List<Change> list = myViewer.getSelectedChanges();
      return list.toArray(new Change[0]);
    }
    else if (VcsDataKeys.CHANGE_LEAD_SELECTION.is(dataId)) {
      final Change highestSelection = myViewer.getHighestLeadSelection();
      return (highestSelection == null) ? new Change[]{} : new Change[]{highestSelection};
    }
    else if (VcsDataKeys.VCS.is(dataId)) {
      Set<AbstractVcs> abstractVcs = ChangesUtil.getAffectedVcses(myViewer.getSelectedChanges(), myProject);
      if (abstractVcs.size() == 1) return Objects.requireNonNull(ContainerUtil.getFirstItem(abstractVcs)).getKeyInstanceMethod();
      return null;
    }
    return super.getData(dataId);
  }
}

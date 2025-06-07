// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CommittedChangesBrowser extends SimpleAsyncChangesBrowser {
  private CommittedChangesBrowserUseCase myUseCase;

  public CommittedChangesBrowser(@NotNull Project project) {
    super(project, false, false);
  }

  @Override
  protected @NotNull List<AnAction> createToolbarActions() {
    return ContainerUtil.append(
      super.createToolbarActions(),
      ActionManager.getInstance().getAction("Vcs.RepositoryChangesBrowserToolbar")
    );
  }

  @Override
  protected @NotNull List<AnAction> createPopupMenuActions() {
    return ContainerUtil.append(
      super.createPopupMenuActions(),
      ActionManager.getInstance().getAction("Vcs.RepositoryChangesBrowserMenu")
    );
  }

  public void setUseCase(final CommittedChangesBrowserUseCase useCase) {
    myUseCase = useCase;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(CommittedChangesBrowserUseCase.DATA_KEY, myUseCase);

    VcsTreeModelData selection = VcsTreeModelData.selected(myViewer);
    sink.lazy(VcsDataKeys.VCS, () -> {
      AbstractVcs selectionVcs = selection.iterateUserObjects(Change.class)
        .map(change -> ChangesUtil.getFilePath(change))
        .map(root -> ProjectLevelVcsManager.getInstance(myProject).getVcsFor(root))
        .filterNotNull()
        .unique()
        .single();
      if (selectionVcs != null) return selectionVcs.getKeyInstanceMethod();
      return null;
    });
  }
}

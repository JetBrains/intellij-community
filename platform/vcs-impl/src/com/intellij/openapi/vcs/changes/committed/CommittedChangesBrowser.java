// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CommittedChangesBrowser extends SimpleChangesBrowser {
  private CommittedChangesBrowserUseCase myUseCase;

  public CommittedChangesBrowser(@NotNull Project project) {
    super(project, false, false);
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    return ContainerUtil.append(
      super.createToolbarActions(),
      ActionManager.getInstance().getAction("Vcs.RepositoryChangesBrowserToolbar")
    );
  }

  @NotNull
  @Override
  protected List<AnAction> createPopupMenuActions() {
    return ContainerUtil.append(
      super.createPopupMenuActions(),
      ActionManager.getInstance().getAction("Vcs.RepositoryChangesBrowserMenu")
    );
  }

  public void setUseCase(final CommittedChangesBrowserUseCase useCase) {
    myUseCase = useCase;
  }

  @Override
  public Object getData(@NotNull @NonNls final String dataId) {
    if (CommittedChangesBrowserUseCase.DATA_KEY.is(dataId)) {
      return myUseCase;
    }
    if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
      DataProvider superProvider = (DataProvider)super.getData(dataId);

      VcsTreeModelData selectedData = VcsTreeModelData.selected(myViewer);
      return CompositeDataProvider.compose(slowId -> getSlowData(slowId, selectedData), superProvider);
    }
    return super.getData(dataId);
  }

  private @Nullable Object getSlowData(@NotNull String dataId, @NotNull VcsTreeModelData selectedData) {
    if (VcsDataKeys.VCS.is(dataId)) {
      AbstractVcs selectionVcs = selectedData.iterateUserObjects(Change.class)
        .map(change -> ChangesUtil.getFilePath(change))
        .map(root -> ProjectLevelVcsManager.getInstance(myProject).getVcsFor(root))
        .filterNotNull()
        .unique()
        .single();
      if (selectionVcs != null) return selectionVcs.getKeyInstanceMethod();
      return null;
    }
    return null;
  }
}

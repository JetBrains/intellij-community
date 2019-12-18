// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProjectCommittedChangesPanel extends CommittedChangesPanel {
  @NotNull private CommittedChangesProvider<?, ?> myProvider;

  public ProjectCommittedChangesPanel(@NotNull Project project, @NotNull CommittedChangesProvider<?, ?> provider) {
    super(project);
    myProvider = provider;

    setup(null, provider.createActions(myBrowser, null));
  }

  @NotNull
  public CommittedChangesProvider<?, ?> getProvider() {
    return myProvider;
  }

  public void setProvider(@NotNull CommittedChangesProvider<?, ?> provider) {
    myProvider = provider;
  }

  @Override
  public void refreshChanges() {
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
    cache.hasCachesForAnyRoot(notEmpty -> {
      if (!notEmpty) {
        myBrowser.getEmptyText().setText(VcsBundle.message("committed.changes.not.loaded.message"));
        return;
      }
      cache.getProjectChangesAsync(
        myProvider.createDefaultSettings(), 0, true,
        committedChangeLists -> updateFilteredModel(committedChangeLists, false),
        vcsExceptions -> AbstractVcsHelper
          .getInstance(myProject).showErrors(vcsExceptions, "Error refreshing VCS history")
      );
    });
  }

  public void clearCaches() {
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
    cache.clearCaches(
      () -> ApplicationManager.getApplication()
        .invokeLater(() -> updateFilteredModel(Collections.emptyList(), true), ModalityState.NON_MODAL, myProject.getDisposed())
    );
  }

  public void passCachedListsToListener(@NotNull VcsConfigurationChangeListener.DetailedNotification notification,
                                        @Nullable VirtualFile root) {
    final List<CommittedChangeList> resultList = new ArrayList<>();
    myBrowser.reportLoadedLists(new CommittedChangeListsListener() {
      @Override
      public void onBeforeStartReport() {
      }

      @Override
      public boolean report(@NotNull CommittedChangeList list) {
        resultList.add(list);
        return false;
      }

      @Override
      public void onAfterEndReport() {
        if (!resultList.isEmpty()) {
          notification.execute(myProject, root, resultList);
        }
      }
    });
  }

  private void updateFilteredModel(List<? extends CommittedChangeList> committedChangeLists, final boolean reset) {
    if (committedChangeLists == null) {
      return;
    }
    setEmptyMessage(!reset);
    myBrowser.setItems(committedChangeLists, CommittedChangesBrowserUseCase.COMMITTED);
  }

  private void setEmptyMessage(boolean changesLoaded) {
    String emptyText;
    if (!changesLoaded) {
      emptyText = VcsBundle.message("committed.changes.not.loaded.message");
    }
    else {
      emptyText = VcsBundle.message("committed.changes.empty.message");
    }
    myBrowser.getEmptyText().setText(emptyText);
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.BufferedListConsumer;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vcs.VcsDataKeys.REMOTE_HISTORY_CHANGED_LISTENER;
import static com.intellij.openapi.vcs.VcsDataKeys.REMOTE_HISTORY_LOCATION;
import static com.intellij.util.WaitForProgressToShow.runOrInvokeLaterAboveProgress;

public class CommittedChangesPanel extends JPanel implements DataProvider, Disposable {
  private static final Logger LOG = Logger.getInstance(CommittedChangesPanel.class);

  private final CommittedChangesTreeBrowser myBrowser;
  private final Project myProject;
  private CommittedChangesProvider myProvider;
  private ChangeBrowserSettings mySettings;
  private final RepositoryLocation myLocation;
  private int myMaxCount = 0;
  private volatile boolean myDisposed;
  private volatile boolean myInLoad;
  private final Consumer<String> myIfNotCachedReloader;

  public CommittedChangesPanel(@NotNull Project project,
                               @NotNull CommittedChangesProvider provider,
                               @NotNull ChangeBrowserSettings settings,
                               @Nullable RepositoryLocation location,
                               @Nullable ActionGroup extraActions) {
    super(new BorderLayout());
    mySettings = settings;
    myProject = project;
    myProvider = provider;
    myLocation = location;
    myBrowser = new CommittedChangesTreeBrowser(project, new ArrayList<>());
    Disposer.register(this, myBrowser);
    add(myBrowser, BorderLayout.CENTER);

    final VcsCommittedViewAuxiliary auxiliary = provider.createActions(myBrowser, location);

    JPanel toolbarPanel = new JPanel();
    toolbarPanel.setLayout(new BoxLayout(toolbarPanel, BoxLayout.X_AXIS));

    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("CommittedChangesToolbar");

    ActionToolbar toolBar = myBrowser.createGroupFilterToolbar(project, group, extraActions,
                                                               auxiliary != null ? auxiliary.getToolbarActions() : Collections.emptyList());
    CommittedChangesFilterComponent filterComponent = new CommittedChangesFilterComponent();
    Disposer.register(this, filterComponent);

    toolbarPanel.add(toolBar.getComponent());
    toolbarPanel.add(Box.createHorizontalGlue());
    toolbarPanel.add(filterComponent);
    filterComponent.setMinimumSize(filterComponent.getPreferredSize());
    filterComponent.setMaximumSize(filterComponent.getPreferredSize());
    myBrowser.setToolBar(toolbarPanel);

    if (auxiliary != null) {
      Disposer.register(this, () -> auxiliary.getCalledOnViewDispose());
      myBrowser.setTableContextMenu(group, auxiliary.getPopupActions());
    }
    else {
      myBrowser.setTableContextMenu(group, Collections.emptyList());
    }

    EmptyAction.registerWithShortcutSet("CommittedChanges.Refresh", CommonShortcuts.getRerun(), this);
    myBrowser.addFilter(filterComponent);
    myIfNotCachedReloader = myLocation == null ? null : s -> refreshChanges(false);
  }

  public RepositoryLocation getRepositoryLocation() {
    return myLocation;
  }

  public void setMaxCount(final int maxCount) {
    myMaxCount = maxCount;
  }

  public void setProvider(final CommittedChangesProvider provider) {
    if (myProvider != provider) {
      myProvider = provider;
      mySettings = provider.createDefaultSettings();
    }
  }

  public void refreshChanges(final boolean cacheOnly) {
    if (myLocation != null) {
      refreshChangesFromLocation();
    }
    else {
      refreshChangesFromCache(cacheOnly);
    }
  }

  private void refreshChangesFromLocation() {
    myBrowser.reset();

    myInLoad = true;
    myBrowser.setLoading(true);
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Loading changes", true) {

      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          Consumer<List<CommittedChangeList>> appender = list ->
            runOrInvokeLaterAboveProgress(() -> myBrowser.append(list), ModalityState.stateForComponent(myBrowser), myProject);
          final BufferedListConsumer<CommittedChangeList> bufferedListConsumer = new BufferedListConsumer<>(30, appender, -1);

          myProvider.loadCommittedChanges(mySettings, myLocation, myMaxCount, new AsynchConsumer<CommittedChangeList>() {
            @Override
            public void finished() {
              bufferedListConsumer.flush();
            }
            @Override
            public void consume(CommittedChangeList committedChangeList) {
              if (myDisposed) {
                indicator.cancel();
              }
              ProgressManager.checkCanceled();
              bufferedListConsumer.consumeOne(committedChangeList);
            }
          });
        }
        catch (final VcsException e) {
          LOG.info(e);
          runOrInvokeLaterAboveProgress(
            () -> Messages.showErrorDialog(myProject, "Error refreshing view: " + StringUtil.join(e.getMessages(), "\n"), "Committed Changes"), null, myProject);
        } finally {
          myInLoad = false;
          myBrowser.setLoading(false);
        }
      }
    });
  }

  public void clearCaches() {
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
    cache.clearCaches(
      () -> ApplicationManager.getApplication().invokeLater(() -> updateFilteredModel(Collections.emptyList(), true), ModalityState.NON_MODAL, myProject.getDisposed()));
  }

  private void refreshChangesFromCache(final boolean cacheOnly) {
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
    cache.hasCachesForAnyRoot(notEmpty -> {
      if (! notEmpty) {
        if (cacheOnly) {
          myBrowser.getEmptyText().setText(VcsBundle.message("committed.changes.not.loaded.message"));
          return;
        }
        if (!CacheSettingsDialog.showSettingsDialog(myProject)) return;
      }
      cache.getProjectChangesAsync(mySettings, myMaxCount, cacheOnly,
                                   committedChangeLists -> updateFilteredModel(committedChangeLists, false),
                                   vcsExceptions -> AbstractVcsHelper.getInstance(myProject).showErrors(vcsExceptions, "Error refreshing VCS history"));
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
    } else {
      emptyText = VcsBundle.message("committed.changes.empty.message");
    }
    myBrowser.getEmptyText().setText(emptyText);
  }

  public void setChangesFilter() {
    CommittedChangesFilterDialog filterDialog = new CommittedChangesFilterDialog(myProject, myProvider.createFilterUI(true), mySettings);
    if (filterDialog.showAndGet()) {
      mySettings = filterDialog.getSettings();
      refreshChanges(false);
    }
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (REMOTE_HISTORY_CHANGED_LISTENER.is(dataId)) return myIfNotCachedReloader;
    if (REMOTE_HISTORY_LOCATION.is(dataId)) return myLocation;
    return myBrowser.getData(dataId);
  }

  @Override
  public void dispose() {
    myDisposed = true;
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

  public boolean isInLoad() {
    return myInLoad;
  }
}

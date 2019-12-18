// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.BufferedListConsumer;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.vcs.VcsDataKeys.REMOTE_HISTORY_CHANGED_LISTENER;
import static com.intellij.openapi.vcs.VcsDataKeys.REMOTE_HISTORY_LOCATION;
import static com.intellij.util.WaitForProgressToShow.runOrInvokeLaterAboveProgress;

public class RepositoryLocationCommittedChangesPanel<S extends ChangeBrowserSettings> extends CommittedChangesPanel {
  private static final Logger LOG = Logger.getInstance(RepositoryLocationCommittedChangesPanel.class);

  @NotNull private final CommittedChangesProvider<?, S> myProvider;
  @NotNull private final RepositoryLocation myRepositoryLocation;

  private S mySettings;
  private int myMaxCount;
  private volatile boolean isLoading;
  private volatile boolean myDisposed;

  public RepositoryLocationCommittedChangesPanel(@NotNull Project project,
                                                 @NotNull CommittedChangesProvider<?, S> provider,
                                                 @NotNull RepositoryLocation repositoryLocation,
                                                 @NotNull ActionGroup extraActions) {
    super(project);
    myProvider = provider;
    myRepositoryLocation = repositoryLocation;
    mySettings = provider.createDefaultSettings();

    setup(extraActions, myProvider.createActions(myBrowser, myRepositoryLocation));
  }

  public void setSettings(@NotNull S settings) {
    mySettings = settings;
  }

  public void setMaxCount(int maxCount) {
    myMaxCount = maxCount;
  }

  public boolean isLoading() {
    return isLoading;
  }

  public void setChangesFilter() {
    CommittedChangesFilterDialog filterDialog = new CommittedChangesFilterDialog(myProject, myProvider.createFilterUI(true), mySettings);
    if (filterDialog.showAndGet()) {
      //noinspection unchecked
      mySettings = (S)filterDialog.getSettings();
      refreshChanges();
    }
  }

  @Override
  public void refreshChanges() {
    myBrowser.reset();

    isLoading = true;
    myBrowser.setLoading(true);
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Loading changes", true) {

      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          Consumer<List<CommittedChangeList>> appender = list ->
            runOrInvokeLaterAboveProgress(() -> myBrowser.append(list), ModalityState.stateForComponent(myBrowser), myProject);
          final BufferedListConsumer<CommittedChangeList> bufferedListConsumer = new BufferedListConsumer<>(30, appender, -1);

          myProvider.loadCommittedChanges(mySettings, myRepositoryLocation, myMaxCount, new AsynchConsumer<CommittedChangeList>() {
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
            () -> Messages
              .showErrorDialog(myProject, "Error refreshing view: " + StringUtil.join(e.getMessages(), "\n"), "Committed Changes"),
            null,
            myProject
          );
        }
        finally {
          isLoading = false;
          myBrowser.setLoading(false);
        }
      }
    });
  }


  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (REMOTE_HISTORY_CHANGED_LISTENER.is(dataId)) return (Consumer<String>)s -> refreshChanges();
    if (REMOTE_HISTORY_LOCATION.is(dataId)) return myRepositoryLocation;
    return super.getData(dataId);
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }
}
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class IncomingChangesViewProvider implements ChangesViewContentProvider {
  private final Project myProject;
  private final MessageBus myBus;
  private CommittedChangesTreeBrowser myBrowser;
  private MessageBusConnection myConnection;
  private final Consumer<List<CommittedChangeList>> myListConsumer;

  public IncomingChangesViewProvider(final Project project, final MessageBus bus) {
    myProject = project;
    myBus = bus;
    myListConsumer = lists -> UIUtil.invokeLaterIfNeeded(() -> {
      myBrowser.getEmptyText().setText(VcsBundle.message("incoming.changes.empty.message"));
      myBrowser.setItems(lists, CommittedChangesBrowserUseCase.INCOMING);
    });
  }

  @Override
  public JComponent initContent() {
    myBrowser = new CommittedChangesTreeBrowser(myProject, Collections.emptyList());
    myBrowser.getEmptyText().setText(VcsBundle.message("incoming.changes.not.loaded.message"));
    ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction("IncomingChangesToolbar");
    final ActionToolbar toolbar = myBrowser.createGroupFilterToolbar(myProject, group, null, Collections.emptyList());
    myBrowser.setToolBar(toolbar.getComponent());
    myBrowser.setTableContextMenu(group, Collections.emptyList());
    myConnection = myBus.connect();
    myConnection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new MyCommittedChangesListener());
    loadChangesToBrowser(false, true);

    return myBrowser;
  }

  @Override
  public void disposeContent() {
    myConnection.disconnect();
    Disposer.dispose(myBrowser);
    myBrowser = null;
  }

  private void updateModel(final boolean inBackground, final boolean refresh) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;
      if (myBrowser != null) {
        loadChangesToBrowser(inBackground, refresh);
      }
    });
  }

  private void loadChangesToBrowser(final boolean inBackground, final boolean refresh) {
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
    cache.hasCachesForAnyRoot(notEmpty -> {
      if (Boolean.TRUE.equals(notEmpty)) {
        final List<CommittedChangeList> list = cache.getCachedIncomingChanges();
        if (list != null) {
          myBrowser.getEmptyText().setText(VcsBundle.message("incoming.changes.empty.message"));
          myBrowser.setItems(list, CommittedChangesBrowserUseCase.INCOMING);
        } else if (refresh) {
          cache.loadIncomingChangesAsync(myListConsumer, inBackground);
        } else {
          myBrowser.getEmptyText().setText(VcsBundle.message("incoming.changes.empty.message"));
          myBrowser.setItems(Collections.emptyList(), CommittedChangesBrowserUseCase.INCOMING);
        }
      }
    });
  }

  private class MyCommittedChangesListener extends CommittedChangesAdapter {
    @Override
    public void changesLoaded(final RepositoryLocation location, final List<CommittedChangeList> changes) {
      updateModel(true, true);
    }

    @Override
    public void incomingChangesUpdated(final List<CommittedChangeList> receivedChanges) {
      updateModel(true, true);
    }

    @Override
    public void presentationChanged() {
      updateModel(true, false);
    }

    @Override
    public void changesCleared() {
      myBrowser.getEmptyText().setText(VcsBundle.message("incoming.changes.empty.message"));
      myBrowser.setItems(Collections.emptyList(), CommittedChangesBrowserUseCase.INCOMING);
    }

    @Override
    public void refreshErrorStatusChanged(@Nullable final VcsException lastError) {
      if (lastError != null) {
        VcsBalloonProblemNotifier.showOverChangesView(myProject, lastError.getMessage(), MessageType.ERROR);
      }
    }
  }
}

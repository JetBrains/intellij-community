// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier.showOverChangesView;
import static java.util.Collections.emptyList;

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
      setIncomingChanges(lists);
    });
  }

  @Override
  public JComponent initContent() {
    myBrowser = new CommittedChangesTreeBrowser(myProject, emptyList());
    myBrowser.getEmptyText().setText(message("incoming.changes.not.loaded.message"));
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("IncomingChangesToolbar");
    final ActionToolbar toolbar = myBrowser.createGroupFilterToolbar(myProject, group, null, emptyList());
    myBrowser.setToolBar(toolbar.getComponent());
    myBrowser.setTableContextMenu(group, emptyList());
    myConnection = myBus.connect();
    myConnection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new MyCommittedChangesListener());
    loadChangesToBrowser(false);

    return myBrowser;
  }

  @Override
  public void disposeContent() {
    myConnection.disconnect();
    Disposer.dispose(myBrowser);
    myBrowser = null;
  }

  private void updateModel() {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;
      if (myBrowser != null) {
        loadChangesToBrowser(true);
      }
    });
  }

  private void loadChangesToBrowser(final boolean inBackground) {
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
    cache.hasCachesForAnyRoot(notEmpty -> {
      if (Boolean.TRUE.equals(notEmpty)) {
        final List<CommittedChangeList> list = cache.getCachedIncomingChanges();
        if (list != null) {
          setIncomingChanges(list);
        }
        else {
          cache.loadIncomingChangesAsync(myListConsumer, inBackground);
        }
      }
    });
  }

  private void setIncomingChanges(@NotNull List<CommittedChangeList> changeLists) {
    myBrowser.getEmptyText().setText(message("incoming.changes.empty.message"));
    myBrowser.setItems(changeLists, CommittedChangesBrowserUseCase.INCOMING);
  }

  private class MyCommittedChangesListener implements CommittedChangesListener {
    @Override
    public void changesLoaded(@NotNull RepositoryLocation location, @NotNull List<CommittedChangeList> changes) {
      updateModel();
    }

    @Override
    public void incomingChangesUpdated(@Nullable List<CommittedChangeList> receivedChanges) {
      updateModel();
    }

    @Override
    public void changesCleared() {
      setIncomingChanges(emptyList());
    }

    @Override
    public void refreshErrorStatusChanged(@Nullable VcsException lastError) {
      if (lastError != null) {
        showOverChangesView(myProject, lastError.getMessage(), MessageType.ERROR);
      }
    }
  }
}

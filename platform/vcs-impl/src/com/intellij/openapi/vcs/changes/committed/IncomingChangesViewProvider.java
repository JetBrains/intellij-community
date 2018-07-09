/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
    public void changesLoaded(final RepositoryLocation location, final List<CommittedChangeList> changes) {
      updateModel(true, true);
    }

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

    public void refreshErrorStatusChanged(@Nullable final VcsException lastError) {
      if (lastError != null) {
        VcsBalloonProblemNotifier.showOverChangesView(myProject, lastError.getMessage(), MessageType.ERROR);
      }
    }
  }
}

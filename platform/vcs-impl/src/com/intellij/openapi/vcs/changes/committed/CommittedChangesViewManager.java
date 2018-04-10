// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class CommittedChangesViewManager implements ChangesViewContentProvider {
  private final MessageBus myBus;
  private MessageBusConnection myConnection;
  private CommittedChangesPanel myComponent;
  private final Project myProject;
  private final VcsListener myVcsListener = new MyVcsListener();

  public CommittedChangesViewManager(final Project project, final MessageBus bus) {
    myProject = project;
    myBus = bus;
  }

  private void updateChangesContent() {
    final CommittedChangesProvider provider = CommittedChangesCache.getInstance(myProject).getProviderForProject();
    if (provider == null) return;

    if (myComponent == null) {
      myComponent = new CommittedChangesPanel(myProject, provider, provider.createDefaultSettings(), null, null);
      myConnection.subscribe(VcsConfigurationChangeListener.BRANCHES_CHANGED, new VcsConfigurationChangeListener.Notification() {
        public void execute(final Project project, final VirtualFile vcsRoot) {
          sendUpdateCachedListsMessage(vcsRoot);
        }
      });
    }
    else {
      myComponent.setProvider(provider);
      // called from listener to notification of vcs root changes
      sendUpdateCachedListsMessage(null);
    }
  }

  private void sendUpdateCachedListsMessage(final VirtualFile vcsRoot) {
    ApplicationManager.getApplication().invokeLater(
      () -> myComponent.passCachedListsToListener(myBus.syncPublisher(VcsConfigurationChangeListener.BRANCHES_CHANGED_RESPONSE),
                                                myProject, vcsRoot), o -> (! myProject.isOpen()) || myProject.isDisposed() || myComponent == null);
  }

  public JComponent initContent() {
    myConnection = myBus.connect();
    myConnection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, myVcsListener);
    myConnection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new MyCommittedChangesListener());
    updateChangesContent();
    myComponent.refreshChanges(true);
    return myComponent;
  }

  public void disposeContent() {
    myConnection.disconnect();
    Disposer.dispose(myComponent);
    myComponent = null;
  }

  private class MyVcsListener implements VcsListener {
    public void directoryMappingChanged() {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!myProject.isDisposed()) {
          updateChangesContent();
        }
      });
    }
  }

  private class MyCommittedChangesListener extends CommittedChangesAdapter {
    public void changesLoaded(RepositoryLocation location, List<CommittedChangeList> changes) {
      presentationChanged();
    }

    @Override
    public void presentationChanged() {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myComponent != null && !myProject.isDisposed()) {
          myComponent.refreshChanges(true);
        }
      });
    }

    public void refreshErrorStatusChanged(@Nullable final VcsException lastError) {
      if (lastError != null) {
        VcsBalloonProblemNotifier.showOverChangesView(myProject, lastError.getMessage(), MessageType.ERROR);
      }
    }
  }
}

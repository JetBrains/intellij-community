// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ui.RemoteStatusChangeNodeDecorator;
import com.intellij.openapi.vcs.update.UpdateFilesHelper;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

@Service(Service.Level.PROJECT)
public final class RemoteRevisionsCache implements VcsListener {

  @Topic.ProjectLevel
  public static final Topic<Runnable> REMOTE_VERSION_CHANGED  = new Topic<>("REMOTE_VERSION_CHANGED", Runnable.class);

  public static final int DEFAULT_REFRESH_INTERVAL = 3 * 60 * 1000;

  private final RemoteRevisionsNumbersCache myRemoteRevisionsNumbersCache;
  private final RemoteRevisionsStateCache myRemoteRevisionsStateCache;

  private final ProjectLevelVcsManager myVcsManager;

  @NotNull private final RemoteStatusChangeNodeDecorator myChangeDecorator;
  private final Project myProject;
  private final ControlledCycle myControlledCycle;

  public static RemoteRevisionsCache getInstance(final Project project) {
    return project.getService(RemoteRevisionsCache.class);
  }

  private RemoteRevisionsCache(final Project project) {
    myProject = project;

    myRemoteRevisionsNumbersCache = new RemoteRevisionsNumbersCache(myProject);
    myRemoteRevisionsStateCache = new RemoteRevisionsStateCache(myProject);

    myChangeDecorator = new RemoteStatusChangeNodeDecorator(this);

    myVcsManager = ProjectLevelVcsManager.getInstance(project);

    final VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(myProject);
    myControlledCycle = new ControlledCycle(project, () -> {
      final boolean shouldBeDone = vcsConfiguration.isChangedOnServerEnabled() && myVcsManager.hasActiveVcss();

      if (shouldBeDone) {
        boolean somethingChanged = myRemoteRevisionsNumbersCache.updateStep();
        somethingChanged |= myRemoteRevisionsStateCache.updateStep();
        if (somethingChanged) {
          myProject.getMessageBus().syncPublisher(REMOTE_VERSION_CHANGED).run();
        }
      }
      return shouldBeDone;
    }, VcsBundle.message("changes.finishing.changed.on.server.update"), DEFAULT_REFRESH_INTERVAL);

    MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this);
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED_IN_PLUGIN, this);

    if ((!myProject.isDefault()) && vcsConfiguration.isChangedOnServerEnabled()) {
      myVcsManager.runAfterInitialization(() -> {
        // do not start if there're no vcses
        if (!myVcsManager.hasActiveVcss() || !vcsConfiguration.isChangedOnServerEnabled()) return;
        myControlledCycle.startIfNotStarted();
      });
    }
  }

  public void updateAutomaticRefreshAlarmState(boolean remoteCacheStateChanged) {
    manageAlarm();
  }

  private void manageAlarm() {
    final VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(myProject);
    if ((! myProject.isDefault()) && myVcsManager.hasActiveVcss() && vcsConfiguration.isChangedOnServerEnabled()) {
      // will check whether is already started inside
      // interval is checked further, this is small and constant
      myControlledCycle.startIfNotStarted();
    } else {
      myControlledCycle.stop();
    }
  }

  @Override
  public void directoryMappingChanged() {
    if (! VcsConfiguration.getInstance(myProject).isChangedOnServerEnabled()) {
      manageAlarm();
    } else {
      BackgroundTaskUtil.executeOnPooledThread(myProject, () -> {
        try {
          myRemoteRevisionsNumbersCache.directoryMappingChanged();
          myRemoteRevisionsStateCache.directoryMappingChanged();
          manageAlarm();
        }
        catch (ProcessCanceledException ignore) {
        }
      });
    }
  }

  public void changeUpdated(@NotNull String path, @NotNull AbstractVcs vcs) {
    if (RemoteDifferenceStrategy.ASK_TREE_PROVIDER.equals(vcs.getRemoteDifferenceStrategy())) {
      myRemoteRevisionsStateCache.changeUpdated(path, vcs);
    }
    else {
      myRemoteRevisionsNumbersCache.changeUpdated(path, vcs);
    }
  }

  public void invalidate(final UpdatedFiles updatedFiles) {
    final Collection<String> newForTree = new ArrayList<>();
    final Collection<String> newForUsual = new ArrayList<>();
    UpdateFilesHelper.iterateAffectedFiles(updatedFiles, pair -> {
      AbstractVcs vcs = myVcsManager.findVcsByName(pair.getSecond());
      if (vcs == null) return;

      if (RemoteDifferenceStrategy.ASK_TREE_PROVIDER.equals(vcs.getRemoteDifferenceStrategy())) {
        newForTree.add(pair.getFirst());
      }
      else {
        newForUsual.add(pair.getFirst());
      }
    });

    myRemoteRevisionsStateCache.invalidate(newForTree);
    myRemoteRevisionsNumbersCache.invalidate(newForUsual);
  }

  public void changeRemoved(@NotNull String path, @NotNull AbstractVcs vcs) {
    if (RemoteDifferenceStrategy.ASK_TREE_PROVIDER.equals(vcs.getRemoteDifferenceStrategy())) {
      myRemoteRevisionsStateCache.changeRemoved(path, vcs);
    }
    else {
      myRemoteRevisionsNumbersCache.changeRemoved(path, vcs);
    }
  }

  /**
   * @return false if not up to date
   */
  public boolean isUpToDate(@NotNull Change change) {
    if (myProject.isDisposed()) return true;
    final AbstractVcs vcs = ChangesUtil.getVcsForChange(change, myProject);
    if (vcs == null) return true;
    final RemoteDifferenceStrategy strategy = vcs.getRemoteDifferenceStrategy();
    if (RemoteDifferenceStrategy.ASK_TREE_PROVIDER.equals(strategy)) {
      return myRemoteRevisionsStateCache.isUpToDate(change, vcs);
    } else {
      return myRemoteRevisionsNumbersCache.isUpToDate(change, vcs);
    }
  }

  @NotNull
  public RemoteStatusChangeNodeDecorator getChangesNodeDecorator() {
    return myChangeDecorator;
  }
}

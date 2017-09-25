/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ui.PlusMinus;
import com.intellij.openapi.vcs.changes.ui.RemoteStatusChangeNodeDecorator;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsInitObject;
import com.intellij.openapi.vcs.update.UpdateFilesHelper;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class RemoteRevisionsCache implements PlusMinus<Pair<String, AbstractVcs>>, VcsListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.RemoteRevisionsCache");

  public static Topic<Runnable> REMOTE_VERSION_CHANGED  = new Topic<>("REMOTE_VERSION_CHANGED", Runnable.class);
  public static final int DEFAULT_REFRESH_INTERVAL = 3 * 60 * 1000;

  private final RemoteRevisionsNumbersCache myRemoteRevisionsNumbersCache;
  private final RemoteRevisionsStateCache myRemoteRevisionsStateCache;

  private final ProjectLevelVcsManager myVcsManager;

  private final RemoteStatusChangeNodeDecorator myChangeDecorator;
  private final Project myProject;
  private final Object myLock;
  private final Map<String, RemoteDifferenceStrategy> myKinds;
  private final ControlledCycle myControlledCycle;

  public static RemoteRevisionsCache getInstance(final Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetService(project, RemoteRevisionsCache.class);
  }

  private RemoteRevisionsCache(final Project project) {
    myProject = project;
    myLock = new Object();

    myRemoteRevisionsNumbersCache = new RemoteRevisionsNumbersCache(myProject);
    myRemoteRevisionsStateCache = new RemoteRevisionsStateCache(myProject);

    myChangeDecorator = new RemoteStatusChangeNodeDecorator(this);

    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this);
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED_IN_PLUGIN, this);
    myKinds = new HashMap<>();

    final VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(myProject);
    myControlledCycle = new ControlledCycle(project, () -> {
      final boolean shouldBeDone = vcsConfiguration.isChangedOnServerEnabled() && myVcsManager.hasActiveVcss();

      if (shouldBeDone) {
        boolean somethingChanged = myRemoteRevisionsNumbersCache.updateStep();
        somethingChanged |= myRemoteRevisionsStateCache.updateStep();
        if (somethingChanged) {
          BackgroundTaskUtil.syncPublisher(myProject, REMOTE_VERSION_CHANGED).run();
        }
      }
      return shouldBeDone;
    }, "Finishing \"changed on server\" update", DEFAULT_REFRESH_INTERVAL);

    updateRoots();

    if ((! myProject.isDefault()) && vcsConfiguration.isChangedOnServerEnabled()) {
      ((ProjectLevelVcsManagerImpl) myVcsManager).addInitializationRequest(VcsInitObject.REMOTE_REVISIONS_CACHE,
                                                                           () -> {
                                                                             // do not start if there're no vcses
                                                                             if (! myVcsManager.hasActiveVcss() || ! vcsConfiguration. isChangedOnServerEnabled()) return;
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

  private void updateRoots() {
    final VcsRoot[] roots = myVcsManager.getAllVcsRoots();
    synchronized (myLock) {
      for (VcsRoot root : roots) {
        final AbstractVcs vcs = root.getVcs();
        if (! myKinds.containsKey(vcs.getName())) {
          myKinds.put(vcs.getName(), vcs.getRemoteDifferenceStrategy());
        }
      }
    }
  }

  @Override
  public void directoryMappingChanged() {
    if (! VcsConfiguration.getInstance(myProject).isChangedOnServerEnabled()) {
      manageAlarm();
    } else {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          updateRoots();
          myRemoteRevisionsNumbersCache.directoryMappingChanged();
          myRemoteRevisionsStateCache.directoryMappingChanged();
          manageAlarm();
        } catch (ProcessCanceledException ignore) {
        }
      });
    }
  }

  @Override
  public void plus(final Pair<String, AbstractVcs> pair) {
    final AbstractVcs vcs = pair.getSecond();
    if (RemoteDifferenceStrategy.ASK_TREE_PROVIDER.equals(vcs.getRemoteDifferenceStrategy())) {
      myRemoteRevisionsStateCache.plus(pair);
    } else {
      myRemoteRevisionsNumbersCache.plus(pair);
    }
  }

  public void invalidate(final UpdatedFiles updatedFiles) {
    final Map<String, RemoteDifferenceStrategy> strategyMap;
    synchronized (myLock) {
      strategyMap = new HashMap<>(myKinds);
    }
    final Collection<String> newForTree = new LinkedList<>();
    final Collection<String> newForUsual = new LinkedList<>();
    UpdateFilesHelper.iterateAffectedFiles(updatedFiles, pair -> {
      final String vcsName = pair.getSecond();
      RemoteDifferenceStrategy strategy = strategyMap.get(vcsName);
      if (strategy == null) {
        final AbstractVcs vcs = myVcsManager.findVcsByName(vcsName);
        if (vcs == null) return;
        strategy = vcs.getRemoteDifferenceStrategy();
      }
      if (RemoteDifferenceStrategy.ASK_TREE_PROVIDER.equals(strategy)) {
        newForTree.add(pair.getFirst());
      } else {
        newForUsual.add(pair.getFirst());
      }
    });

    myRemoteRevisionsStateCache.invalidate(newForTree);
    myRemoteRevisionsNumbersCache.invalidate(newForUsual);
  }

  @Override
  public void minus(Pair<String, AbstractVcs> pair) {
    final AbstractVcs vcs = pair.getSecond();
    if (RemoteDifferenceStrategy.ASK_TREE_PROVIDER.equals(vcs.getRemoteDifferenceStrategy())) {
      myRemoteRevisionsStateCache.minus(pair);
    } else {
      myRemoteRevisionsNumbersCache.minus(pair);
    }
  }

  /**
   * @return false if not up to date
   */
  public boolean isUpToDate(final Change change) {
    final AbstractVcs vcs = ChangesUtil.getVcsForChange(change, myProject);
    if (vcs == null) return true;
    final RemoteDifferenceStrategy strategy = vcs.getRemoteDifferenceStrategy();
    if (RemoteDifferenceStrategy.ASK_TREE_PROVIDER.equals(strategy)) {
      return myRemoteRevisionsStateCache.isUpToDate(change);
    } else {
      return myRemoteRevisionsNumbersCache.isUpToDate(change);
    }
  }

  public RemoteStatusChangeNodeDecorator getChangesNodeDecorator() {
    return myChangeDecorator;
  }
}

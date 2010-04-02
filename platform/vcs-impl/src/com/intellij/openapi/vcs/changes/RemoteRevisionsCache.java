/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.lifecycle.AtomicSectionsAware;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ui.RemoteStatusChangeNodeDecorator;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsInitObject;
import com.intellij.openapi.vcs.update.UpdateFilesHelper;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.util.Consumer;
import com.intellij.util.messages.Topic;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class RemoteRevisionsCache implements PlusMinus<Pair<String, AbstractVcs>>, VcsListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.RemoteRevisionsCache");

  public static Topic<Runnable> REMOTE_VERSION_CHANGED  = new Topic<Runnable>("REMOTE_VERSION_CHANGED", Runnable.class);

  private final RemoteRevisionsNumbersCache myRemoteRevisionsNumbersCache;
  private final RemoteRevisionsStateCache myRemoteRevisionsStateCache;

  private final ProjectLevelVcsManager myVcsManager;

  private final RemoteStatusChangeNodeDecorator myChangeDecorator;
  private final Project myProject;
  private final Object myLock;
  private final Map<String, RemoteDifferenceStrategy> myKinds;
  private final ControlledCycle myControlledCycle;

  public static RemoteRevisionsCache getInstance(final Project project) {
    return ServiceManager.getService(project, RemoteRevisionsCache.class);
  }

  private RemoteRevisionsCache(final Project project) {
    myProject = project;
    myLock = new Object();

    myRemoteRevisionsNumbersCache = new RemoteRevisionsNumbersCache(myProject);
    myRemoteRevisionsStateCache = new RemoteRevisionsStateCache(myProject);

    myChangeDecorator = new RemoteStatusChangeNodeDecorator(this);

    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myVcsManager.addVcsListener(this);
    myKinds = new HashMap<String, RemoteDifferenceStrategy>();
    Disposer.register(project, new Disposable() {
      public void dispose() {
        myVcsManager.removeVcsListener(RemoteRevisionsCache.this);
      }
    });
    updateKinds();
    myControlledCycle = new ControlledCycle(project, new ControlledCycle.MyCallback() {
      public boolean call(final AtomicSectionsAware atomicSectionsAware) {
        atomicSectionsAware.checkShouldExit();
        final boolean shouldBeDone = VcsConfiguration.getInstance(myProject).CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND;
        if (shouldBeDone) {
          boolean somethingChanged = myRemoteRevisionsNumbersCache.updateStep(atomicSectionsAware);
          atomicSectionsAware.checkShouldExit();
          somethingChanged |= myRemoteRevisionsStateCache.updateStep(atomicSectionsAware);
          if (somethingChanged) {
            myProject.getMessageBus().syncPublisher(REMOTE_VERSION_CHANGED).run();
          }
        }
        return shouldBeDone;
      }
    }, "Finishing \"changed on server\" update", 3 * 60 * 1000);
    if ((! myProject.isDefault()) && VcsConfiguration.getInstance(myProject).CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND) {
      ((ProjectLevelVcsManagerImpl) myVcsManager).addInitializationRequest(VcsInitObject.REMOTE_REVISIONS_CACHE,
                                                                           new Runnable() {
                                                                             public void run() {
                                                                               myControlledCycle.start();
                                                                             }
                                                                           });
    }
  }

  public void startRefreshInBackground() {
    if (myProject.isDefault()) return;
    myControlledCycle.start();
  }

  private void updateKinds() {
    final VcsRoot[] roots = myVcsManager.getAllVcsRoots();
    synchronized (myLock) {
      for (VcsRoot root : roots) {
        final AbstractVcs vcs = root.vcs;
        if (! myKinds.containsKey(vcs.getName())) {
          myKinds.put(vcs.getName(), vcs.getRemoteDifferenceStrategy());
        }
      }
    }
  }

  public void directoryMappingChanged() {
    updateKinds();
    myRemoteRevisionsNumbersCache.directoryMappingChanged();
    myRemoteRevisionsStateCache.directoryMappingChanged();
  }

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
      strategyMap = new HashMap<String, RemoteDifferenceStrategy>(myKinds);
    }
    final Collection<String> newForTree = new LinkedList<String>();
    final Collection<String> newForUsual = new LinkedList<String>();
    UpdateFilesHelper.iterateAffectedFiles(updatedFiles, new Consumer<Pair<String, String>>() {
      public void consume(final Pair<String, String> pair) {
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
      }
    });

    myRemoteRevisionsStateCache.invalidate(newForTree);
    myRemoteRevisionsNumbersCache.invalidate(newForUsual);
  }

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

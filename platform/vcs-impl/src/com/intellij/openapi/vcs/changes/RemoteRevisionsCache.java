package com.intellij.openapi.vcs.changes;

import com.intellij.lifecycle.ControlledAlarmFactory;
import com.intellij.lifecycle.SlowlyClosingAlarm;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ui.RemoteStatusChangeNodeDecorator;
import com.intellij.openapi.vcs.update.UpdateFilesHelper;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.Alarm;
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
  private final LocalFileSystem myLfs;

  private final RemoteStatusChangeNodeDecorator myChangeDecorator;
  private final Project myProject;
  private final Object myLock;
  private final Map<String, RemoteDifferenceStrategy> myKinds;

  public static RemoteRevisionsCache getInstance(final Project project) {
    return ServiceManager.getService(project, RemoteRevisionsCache.class);
  }

  private RemoteRevisionsCache(final Project project) {
    myProject = project;
    myLfs = LocalFileSystem.getInstance();
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
    final MyRecursiveUpdateRequest request = new MyRecursiveUpdateRequest(project, new Runnable() {
      public void run() {
        boolean somethingChanged = myRemoteRevisionsNumbersCache.updateStep();
        somethingChanged |= myRemoteRevisionsStateCache.updateStep();
        if (somethingChanged) {
          myProject.getMessageBus().syncPublisher(REMOTE_VERSION_CHANGED).run();
        }
      }
    });
    request.start();
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

  private static class MyRecursiveUpdateRequest implements Runnable {
    private final Alarm mySimpleAlarm;
    private final SlowlyClosingAlarm myControlledAlarm;
    // this interval is also to check for not initialized paths, so it is rather small
    private static final int ourRefreshInterval = 1000;
    private final Runnable myRunnable;

    private MyRecursiveUpdateRequest(final Project project, final Runnable runnable) {
      myRunnable = new Runnable() {
        public void run() {
          try {
            runnable.run();
          } catch (ProcessCanceledException e) {
            //
          } catch (RuntimeException e) {
            LOG.info(e);
          }
          mySimpleAlarm.addRequest(MyRecursiveUpdateRequest.this, ourRefreshInterval);
        }
      };
      mySimpleAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, project);
      myControlledAlarm = ControlledAlarmFactory.createOnApplicationPooledThread(project);
    }

    public void start() {
      mySimpleAlarm.addRequest(this, ourRefreshInterval);
    }

    public void run() {
      try {
        myControlledAlarm.checkShouldExit();
        myControlledAlarm.addRequest(myRunnable);
      } catch (ProcessCanceledException e) {
        //
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

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

import static com.intellij.vcsUtil.VcsUtil.*;

/**
 * for vcses where it is reasonable to ask revision of each item separately
 */
@ApiStatus.Internal
public final class RemoteRevisionsNumbersCache implements ChangesOnServerTracker {
  public static final Logger LOG = Logger.getInstance(RemoteRevisionsNumbersCache.class);

  // every hour (time unit to check for server commits)
  // default, actual in settings
  private static final long ourRottenPeriod = 3600 * 1000;
  private final @NotNull Map<String, Pair<VcsRoot, VcsRevisionNumber>> myData = new HashMap<>();
  private final @NotNull Map<VcsRoot, LazyRefreshingSelfQueue<String>> myRefreshingQueues = Collections.synchronizedMap(new HashMap<>());
  private final @NotNull Map<String, VcsRevisionNumber> myLatestRevisionsMap = new HashMap<>();
  private boolean mySomethingChanged;

  private final @NotNull Object myLock = new Object();

  public static final VcsRevisionNumber NOT_LOADED = new VcsRevisionNumber() {
    @NotNull
    @Override
    public String asString() {
      return "NOT_LOADED";
    }

    @Override
    public int compareTo(@NotNull VcsRevisionNumber o) {
      return o == this ? 0 : -1;
    }
  };
  public static final VcsRevisionNumber UNKNOWN = new VcsRevisionNumber() {
    @NotNull
    @Override
    public String asString() {
      return "UNKNOWN";
    }

    @Override
    public int compareTo(@NotNull VcsRevisionNumber o) {
      return o == this ? 0 : -1;
    }
  };
  private final @NotNull Project myProject;

  RemoteRevisionsNumbersCache(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public boolean updateStep() {
    mySomethingChanged = false;
    // copy under lock
    final HashMap<VcsRoot, LazyRefreshingSelfQueue> copyMap;
    synchronized (myLock) {
      copyMap = new HashMap<>(myRefreshingQueues);
    }

    // filter only items for vcs roots that support background operations
    for (Iterator<Map.Entry<VcsRoot, LazyRefreshingSelfQueue>> iterator = copyMap.entrySet().iterator(); iterator.hasNext();) {
      final Map.Entry<VcsRoot, LazyRefreshingSelfQueue> entry = iterator.next();
      final VcsRoot key = entry.getKey();
      final boolean backgroundOperationsAllowed = key.getVcs().isVcsBackgroundOperationsAllowed(key.getPath());
      LOG.debug("backgroundOperationsAllowed: " + backgroundOperationsAllowed + " for " + key.getVcs().getName() + ", " + key.getPath().getPath());
      if (! backgroundOperationsAllowed) {
        iterator.remove();
      }
    }
    LOG.debug("queues refresh started, queues: " + copyMap.size());
    // refresh "up to date" info
    for (LazyRefreshingSelfQueue queue : copyMap.values()) {
      if (myProject.isDisposed()) throw new ProcessCanceledException();
      queue.updateStep();
    }
    return mySomethingChanged;
  }

  @Override
  public void directoryMappingChanged() {
    // copy myData under lock
    HashSet<String> keys;
    synchronized (myLock) {
      keys = new HashSet<>(myData.keySet());
    }
    // collect new vcs for scheduled files
    final Map<String, Pair<VirtualFile, AbstractVcs>> vFiles = new HashMap<>();
    for (String key : keys) {
      final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(key));
      final AbstractVcs newVcs = (vf == null) ? null : getVcsFor(myProject, vf);
      vFiles.put(key, vf == null ? Pair.create(null, null) : Pair.create(vf, newVcs));
    }
    synchronized (myLock) {
      keys = new HashSet<>(myData.keySet());
      for (String key : keys) {
        final Pair<VcsRoot, VcsRevisionNumber> value = myData.get(key);
        final VcsRoot storedVcsRoot = value.getFirst();
        final Pair<VirtualFile, AbstractVcs> pair = vFiles.get(key);
        if (pair == null) {
          continue; // already added with new mappings
        }
        final VirtualFile vf = pair.getFirst();
        final AbstractVcs newVcs = pair.getSecond();
        final VirtualFile newRoot = newVcs != null ? getVcsRootFor(myProject, vf) : null;

        if (newRoot == null) {
          myData.remove(key);
          getQueue(storedVcsRoot).forceRemove(key);
        }
        else {
          final VcsRoot newVcsRoot = new VcsRoot(newVcs, newRoot);
          if (! storedVcsRoot.equals(newVcsRoot)) {
            switchVcs(storedVcsRoot, newVcsRoot, key);
          }
        }
      }
    }
  }

  private void switchVcs(final VcsRoot oldVcsRoot, final VcsRoot newVcsRoot, final String key) {
    synchronized (myLock) {
      final LazyRefreshingSelfQueue<String> oldQueue = getQueue(oldVcsRoot);
      final LazyRefreshingSelfQueue<String> newQueue = getQueue(newVcsRoot);
      myData.put(key, Pair.create(newVcsRoot, NOT_LOADED));
      oldQueue.forceRemove(key);
      newQueue.addRequest(key);
    }
  }

  @Override
  public void changeUpdated(@NotNull String path, @NotNull AbstractVcs vcs) {
    // does not support
    if (vcs.getDiffProvider() == null) return;

    final VirtualFile root = getVcsRootFor(myProject, getFilePath(path, false));
    if (root == null) return;

    final VcsRoot vcsRoot = new VcsRoot(vcs, root);

    synchronized (myLock) {
      final Pair<VcsRoot, VcsRevisionNumber> value = myData.get(path);
      if (value == null) {
        final LazyRefreshingSelfQueue<String> queue = getQueue(vcsRoot);
        myData.put(path, Pair.create(vcsRoot, NOT_LOADED));
        queue.addRequest(path);
      }
      else if (!value.getFirst().equals(vcsRoot)) {
        switchVcs(value.getFirst(), vcsRoot, path);
      }
    }
  }

  @Override
  public void invalidate(final Collection<String> paths) {
    synchronized (myLock) {
      for (String path : paths) {
        final Pair<VcsRoot, VcsRevisionNumber> pair = myData.remove(path);
        if (pair != null) {
          // vcs [root] seems to not change
          final VcsRoot vcsRoot = pair.getFirst();
          final LazyRefreshingSelfQueue<String> queue = getQueue(vcsRoot);
          queue.forceRemove(path);
          queue.addRequest(path);
          myData.put(path, Pair.create(vcsRoot, NOT_LOADED));
        }
      }
    }
  }

  @Override
  public void changeRemoved(@NotNull String path, @NotNull AbstractVcs vcs) {
    // does not support
    if (vcs.getDiffProvider() == null) return;
    final VirtualFile root = getVcsRootFor(myProject, getFilePath(path, false));
    if (root == null) return;

    final LazyRefreshingSelfQueue<String> queue;
    synchronized (myLock) {
      queue = getQueue(new VcsRoot(vcs, root));
      myData.remove(path);
    }
    queue.forceRemove(path);
  }

  // +-
  @NotNull
  private LazyRefreshingSelfQueue<String> getQueue(final VcsRoot vcsRoot) {
    synchronized (myLock) {
      LazyRefreshingSelfQueue<String> queue = myRefreshingQueues.get(vcsRoot);
      if (queue != null) return queue;

      queue = new LazyRefreshingSelfQueue<>(
        () -> {
          int interval = VcsConfiguration.getInstance(myProject).CHANGED_ON_SERVER_INTERVAL;
          return interval > 0 ? interval * 60000L : ourRottenPeriod;
        },
        new MyShouldUpdateChecker(vcsRoot),
        new MyUpdater(vcsRoot)
      );
      myRefreshingQueues.put(vcsRoot, queue);
      return queue;
    }
  }

  private class MyUpdater implements Consumer<String> {
    private final VcsRoot myVcsRoot;

    MyUpdater(final VcsRoot vcsRoot) {
      myVcsRoot = vcsRoot;
    }

    @Override
    public void consume(String s) {
      LOG.debug("update for: " + s);
      //todo check canceled - check VCS's ready for asynchronous queries
      // get last remote revision for file
      final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(s));
      final ItemLatestState state;
      final DiffProvider diffProvider = myVcsRoot.getVcs().getDiffProvider();
      if (vf == null) {
        // doesnt matter if directory or not
        state = diffProvider.getLastRevision(getFilePath(s, false));
      } else {
        state = diffProvider.getLastRevision(vf);
      }
      final VcsRevisionNumber newNumber = (state == null) || state.isDefaultHead() ? UNKNOWN : state.getNumber();

      final Pair<VcsRoot, VcsRevisionNumber> oldPair;
      // update value in cache
      synchronized (myLock) {
        oldPair = myData.get(s);
        myData.put(s, Pair.create(myVcsRoot, newNumber));
      }

      if (oldPair == null || oldPair.getSecond().compareTo(newNumber) != 0) {
        LOG.debug("refresh triggered by " + s);
        mySomethingChanged = true;
      }
    }
  }

  private class MyShouldUpdateChecker implements Computable<Boolean> {
    private final VcsRoot myVcsRoot;

    MyShouldUpdateChecker(final VcsRoot vcsRoot) {
      myVcsRoot = vcsRoot;
    }

    // Check if currently cached vcs root latest revision is less than latest vcs root revision
    // => update should be performed in this case
    @Override
    public Boolean compute() {
      final AbstractVcs vcs = myVcsRoot.getVcs();
      // won't be called in parallel for same vcs -> just synchronized map is ok
      final String vcsName = vcs.getName();
      LOG.debug("should update for: " + vcsName + " root: " + myVcsRoot.getPath().getPath());
      final VcsRevisionNumber latestNew = vcs.getDiffProvider().getLatestCommittedRevision(myVcsRoot.getPath());

      // TODO: Why vcsName is used as key and not myVcsRoot.getKey()???
      // TODO: This seems to be invalid logic as we get latest revision for vcs root
      final VcsRevisionNumber latestKnown = myLatestRevisionsMap.get(vcsName);
      // not known
      if (latestNew == null) return true;
      if ((latestKnown == null) || (latestNew.compareTo(latestKnown) != 0)) {
        myLatestRevisionsMap.put(vcsName, latestNew);
        return true;
      }
      return false;
    }
  }

  private VcsRevisionNumber getNumber(final String path) {
    synchronized (myLock) {
      final Pair<VcsRoot, VcsRevisionNumber> pair = myData.get(path);
      return pair == null ? NOT_LOADED : pair.getSecond();
    }
  }

  @Override
  public boolean isUpToDate(@NotNull Change change, @NotNull AbstractVcs vcs) {
    if (change.getBeforeRevision() != null && change.getAfterRevision() != null && (! change.isMoved()) && (! change.isRenamed())) {
      return getRevisionState(change.getBeforeRevision());
    }
    return getRevisionState(change.getBeforeRevision()) && getRevisionState(change.getAfterRevision());
  }

  /**
   * Returns {@code true} if passed revision is up to date, comparing to latest repository revision.
   */
  private boolean getRevisionState(final ContentRevision revision) {
    if (revision != null) {
      // TODO: Seems peg revision should also be tracked here.
      final VcsRevisionNumber local = revision.getRevisionNumber();
      final String path = revision.getFile().getPath();
      final VcsRevisionNumber remote = getNumber(path);

      return NOT_LOADED == remote || UNKNOWN == remote || local.compareTo(remote) >= 0;
    }
    return true;
  }
}

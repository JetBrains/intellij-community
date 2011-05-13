/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import git4idea.history.browser.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author irengrig
 */
public class DetailsLoaderImpl implements DetailsLoader {
  private final static Logger LOG = Logger.getInstance("#git4idea.history.wholeTree.DetailsLoaderImpl");

  private final static int ourLoadSize = 20;
  private final static long ourRefsReload = 120000;

  private final BackgroundTaskQueue myQueue;
  private final Map<VirtualFile, CommitIdsHolder<AbstractHash>> myLoadIdsGatherer;
  private final Map<VirtualFile, LowLevelAccess> myAccesses;
  private DetailsCache myDetailsCache;
  private final Project myProject;
  private final Map<VirtualFile, SymbolicRefs> myRefs;
  private long myRefsReloadTick;

  private final Object myLock;

  public DetailsLoaderImpl(Project project) {
    myQueue = new BackgroundTaskQueue(project, "Git log details");
    myProject = project;
    myLoadIdsGatherer = new HashMap<VirtualFile, CommitIdsHolder<AbstractHash>>();
    myAccesses = new HashMap<VirtualFile, LowLevelAccess>();
    myRefs = new HashMap<VirtualFile, SymbolicRefs>();
    myRefsReloadTick = 0;
    myLock = new Object();
    scheduleRefs();
  }

  private void scheduleRefs() {
    myQueue.run(new RefsLoader(myProject));
  }

  public void setDetailsCache(DetailsCache detailsCache) {
    synchronized (myLock) {
      myDetailsCache = detailsCache;
    }
  }

  public void setRoots(final Collection<VirtualFile> roots) {
    synchronized (myLock) {
      myLoadIdsGatherer.clear();
      myAccesses.clear();
      for (VirtualFile root : roots) {
        myLoadIdsGatherer.put(root, new CommitIdsHolder<AbstractHash>());
        myAccesses.put(root, new LowLevelAccessImpl(myProject, root));
      }
      myRefsReloadTick = 0;
    }
  }

  @Override
  public void load(MultiMap<VirtualFile,AbstractHash> hashes) {
    final long now = System.currentTimeMillis();
    synchronized (myLock) {
      if (now - myRefsReloadTick >= ourRefsReload) {
        scheduleRefs();
      }
      myRefsReloadTick = now;
      for (VirtualFile root : hashes.keySet()) {
        final CommitIdsHolder<AbstractHash> holder = myLoadIdsGatherer.get(root);
        holder.add(hashes.get(root));
        myQueue.run(new Worker(myProject, root, myAccesses.get(root), myDetailsCache, myQueue));
      }
    }
  }

  private class RefsLoader extends Task.Backgroundable {
    private RefsLoader(@Nullable Project project) {
      super(project, "Reload symbolic references", false, BackgroundFromStartOption.getInstance());
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      synchronized (myLock) {
        for (VirtualFile file : myAccesses.keySet()) {
          final LowLevelAccess access = myAccesses.get(file);
          try {
            myRefs.put(file, access.getRefs());
          }
          catch (VcsException e) {
            LOG.info(e);
          }
        }
      }
    }
  }

  private class Worker extends Task.Backgroundable {
    private final LowLevelAccess myAccess;
    private final DetailsCache myDetailsCache;
    private final BackgroundTaskQueue myQueue;
    private final VirtualFile myVirtualFile;

    private Worker(@Nullable final Project project,
                   final VirtualFile virtualFile,
                   final LowLevelAccess access,
                   final DetailsCache detailsCache, final BackgroundTaskQueue queue) {
      super(project, "Load git commits details", false, BackgroundFromStartOption.getInstance());
      myVirtualFile = virtualFile;
      myAccess = access;
      myDetailsCache = detailsCache;
      myQueue = queue;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      final CommitIdsHolder<AbstractHash> holder;
      final SymbolicRefs refs;
      synchronized (myLock) {
        holder = myLoadIdsGatherer.get(myVirtualFile);
        refs = myRefs.get(myAccess.getRoot());
      }
      if (holder == null) return;
      final Collection<AbstractHash> hashes = holder.get(ourLoadSize);
      try {
        loadDetails(hashes, refs);
      }
      catch (VcsException e) {
        LOG.info(e);
        for (AbstractHash hash : hashes) {
          try {
            loadDetails(Collections.singletonList(hash), refs);
          }
          catch (VcsException e1) {
            LOG.info(e1);
            myDetailsCache.acceptAnswer(Collections.singletonList(createNotLoadedCommit(hash)), myAccess.getRoot());
          }
        }
      }
      if (holder.haveData()) {
        myQueue.run(this);
      }
    }

    private void loadDetails(Collection<AbstractHash> hashes, SymbolicRefs refs) throws VcsException {
      final List<String> converted = new ArrayList<String>();
      for (final AbstractHash hash : hashes) {
        if (myDetailsCache.convert(myVirtualFile, hash) == null) {
          converted.add(hash.getString());
        }
      }
      if (! hashes.isEmpty()) {
          final Collection<GitCommit> result = myAccess.getCommitDetails(converted, refs);
          if (result != null && (! result.isEmpty())) {
            myDetailsCache.acceptAnswer(result, myAccess.getRoot());
            for (GitCommit gitCommit : result) {
              converted.remove(gitCommit.getShortHash().getString());
            }
          }
          if (! converted.isEmpty()) {
            // todo this is bad
            final Collection<GitCommit> error = new ArrayList<GitCommit>();
            for (String s : converted) {
              error.add(createNotLoadedCommit(AbstractHash.create(s)));
            }
            myDetailsCache.acceptAnswer(error, myAccess.getRoot());
          }
      }
    }

    private GitCommit createNotLoadedCommit(AbstractHash shortHash) {
      final String notKnown = "Can not load";
      return new GitCommit(shortHash, SHAHash.emulate(shortHash), notKnown, notKnown, new Date(0), notKnown,
                              "Can not load details", Collections.<String>emptySet(), Collections.<FilePath>emptyList(), notKnown,
                              notKnown, Collections.<String>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList(),
                              Collections.<Change>emptyList(), 0);
    }
  }
}

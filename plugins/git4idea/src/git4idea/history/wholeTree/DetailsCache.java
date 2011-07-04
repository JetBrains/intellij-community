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

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SLRUMap;
import git4idea.history.browser.GitCommit;
import git4idea.history.browser.LowLevelAccessImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author irengrig
 */
public class DetailsCache {
  private final static int ourSize = 400;
  private final SLRUMap<Pair<VirtualFile, AbstractHash>, GitCommit> myCache;
  private final SLRUMap<Pair<VirtualFile, AbstractHash>, List<String>> myBranches;
  private final Project myProject;
  private final DetailsLoaderImpl myDetailsLoader;
  private final ModalityState myModalityState;
  private final BackgroundTaskQueue myQueue;
  private UIRefresh myRefresh;
  private final Map<VirtualFile, Map<AbstractHash, String>> myStash;
  private final Object myLock;
  private final static Logger LOG = Logger.getInstance("git4idea.history.wholeTree.DetailsCache");

  public DetailsCache(final Project project,
                      final UIRefresh uiRefresh,
                      final DetailsLoaderImpl detailsLoader,
                      final ModalityState modalityState,
                      final BackgroundTaskQueue queue) {
    myProject = project;
    myDetailsLoader = detailsLoader;
    myModalityState = modalityState;
    myQueue = queue;
    myStash = new HashMap<VirtualFile, Map<AbstractHash,String>>();
    myRefresh = uiRefresh;
    myLock = new Object();
    myCache = new SLRUMap<Pair<VirtualFile, AbstractHash>, GitCommit>(ourSize, 50);
    myBranches = new SLRUMap<Pair<VirtualFile, AbstractHash>, List<String>>(10, 10);
  }

  public GitCommit convert(final VirtualFile root, final AbstractHash hash) {
    synchronized (myLock) {
      return myCache.get(new Pair<VirtualFile, AbstractHash>(root, hash));
    }
  }

  public void acceptQuestion(final MultiMap<VirtualFile,AbstractHash> hashes) {
    if (hashes.isEmpty()) return;
    myDetailsLoader.load(hashes);
  }

  public void acceptAnswer(final Collection<GitCommit> commits, final VirtualFile root) {
    synchronized (myLock) {
      for (GitCommit commit : commits) {
        myCache.put(new Pair<VirtualFile, AbstractHash>(root, commit.getShortHash()), commit);
      }
    }
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        myRefresh.detailsLoaded();
      }
    });
  }

  public void rootsChanged(final Collection<VirtualFile> roots) {
    myDetailsLoader.setRoots(roots);
  }

  public void putBranches(final VirtualFile root, final AbstractHash hash, final List<String> s) {
    synchronized (myLock) {
      myBranches.put(new Pair<VirtualFile, AbstractHash>(root, hash), s);
    }
  }

  public List<String> getBranches(final VirtualFile root, final AbstractHash hash) {
    synchronized (myLock) {
      return myBranches.get(new Pair<VirtualFile, AbstractHash>(root, hash));
    }
  }

  public void resetAsideCaches() {
    synchronized (myLock) {
      myBranches.clear();
      myStash.clear();
      myCache.clear();
    }
  }

  public void putStash(final VirtualFile root, final Map<AbstractHash, String> stash) {
    synchronized (myLock) {
      myStash.put(root, stash);
    }
  }

  @Nullable
  public String getStashName(final VirtualFile root, final AbstractHash hash) {
    synchronized (myLock) {
      final Map<AbstractHash, String> map = myStash.get(root);
      return map == null ? null : map.get(hash);
    }
  }

  public void clearBranches() {
    synchronized (myLock) {
      myBranches.clear();
    }
  }

  public void loadAndPutBranches(final VirtualFile root,
                                 final AbstractHash abstractHash,
                                 final Consumer<List<String>> continuation, final Processor<AbstractHash> recheck) {
    myQueue.run(new Task.Backgroundable(myProject, "Load contained in branches", true, BackgroundFromStartOption.getInstance()) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        if (! recheck.process(abstractHash)) return;
        if (getBranches(root, abstractHash) != null) return;
        final List<String> branches;
        try {
          branches = new LowLevelAccessImpl(myProject, root).getBranchesWithCommit(abstractHash.getString());
        }
        catch (VcsException e) {
          LOG.info(e);
          return;
        }
        putBranches(root, abstractHash, branches);
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            continuation.consume(branches);
          }
        });
      }
    });
  }
}

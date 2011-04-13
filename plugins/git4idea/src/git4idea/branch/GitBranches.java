/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.branch;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import git4idea.GitBranch;
import git4idea.GitVcs;
import git4idea.vfs.GitReferenceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Container and tracker of git branches information.
 * Listens to branch change and updates information here.
 * Subscribe a {@link GitBranchesListener} to get notified for current branch and other branch configuration changes.
 * @author Kirill Likhodedov
 */
public class GitBranches implements GitReferenceListener {
  private static final Logger LOG = Logger.getInstance(GitBranches.class.getName());
  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;

  private final EventDispatcher<GitBranchesListener> myListeners = EventDispatcher.create(GitBranchesListener.class);
  private Map<VirtualFile, GitBranch> myCurrentBranches = new HashMap<VirtualFile, GitBranch>();
  private final Object myCurrentBranchesLock = new Object();
  private ChangeListManager myChangeListManager;
  private GitVcs myVcs;
  private final AtomicBoolean mySoleUseControl;

  public GitBranches(Project project, ChangeListManager changeListManager, ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myChangeListManager = changeListManager;
    myVcsManager = vcsManager;
    mySoleUseControl = new AtomicBoolean(false);
  }

  public static GitBranches getInstance(Project project) {
    return ServiceManager.getService(project, GitBranches.class);
  }

  @Override
  public void referencesChanged(VirtualFile root) {
    updateBranchesInfo(root);
  }

  public void activate(GitVcs vcs) {
    myVcs = vcs;
    myVcs.addGitReferenceListener(this);
  }

  public void deactivate() {
    if (myVcs != null) {
      myVcs.removeGitReferenceListener(this);
    }
  }

  /**
   * Returns branch that is active (current) in the repository in which the given file resides.
   * @param file file to determine branch.
   * @return current branch or null if the file is null, not under git vcs, unversioned, or branch information is not available for it.
   */
  @Nullable
  public GitBranch getCurrentBranch(VirtualFile file) {
    if (file == null) { return null; }
    final AbstractVcs vcs = myVcsManager.getVcsFor(file);
    if (vcs == null || !(vcs instanceof GitVcs)) { return null; }
    final VirtualFile vcsRoot = myVcsManager.getVcsRootFor(file);
    if (vcsRoot == null) { return null; }

    synchronized (myCurrentBranchesLock) {
      return myCurrentBranches.get(vcsRoot);
    }
  }

  public void addListener(@NotNull GitBranchesListener listener, @NotNull Disposable parentDisposable) {
    myListeners.addListener(listener,parentDisposable);
  }

  /**
   * Updates branch information for the given root.
   * If root is null, updates branch information for all Git roots in the project.
   * @see #fullyUpdateBranchesInfo(java.util.Collection)
   */
  private void updateBranchesInfo(final VirtualFile root) {
    if (root == null) { // all roots may be affected
      Collection<VirtualFile> roots = new ArrayList<VirtualFile>(1);
      for (VcsRoot vcsRoot : myVcsManager.getAllVcsRoots()) {
        if (vcsRoot.vcs != null && vcsRoot.vcs instanceof GitVcs && vcsRoot.path != null) {
          roots.add(vcsRoot.path);
        }
      }
      fullyUpdateBranchesInfo(roots);
      return;
    }

    final Task.Backgroundable task = new Task.Backgroundable(myProject, "Git: refresh current branch") {
      @Override public void run(@NotNull ProgressIndicator indicator) {
        assert ! mySoleUseControl.get();
        mySoleUseControl.set(true);
        try {
          GitBranch currentBranch = GitBranch.current(myProject, root);
          synchronized (myCurrentBranchesLock) {
            myCurrentBranches.put(root, currentBranch);
          }
          notifyListeners();
        } catch (VcsException e) {
          LOG.info("Exception while trying to get current branch for root " + root, e);
          // doing nothing - null will be set to myCurrentBranchName
        } finally {
          mySoleUseControl.set(false);
        }
      }
    };
    GitVcs.runInBackground(task);
  }

  private void fullyUpdateBranchesInfo(final Collection<VirtualFile> roots) {
    if (roots == null) { return; }
    final Task.Backgroundable task = new Task.Backgroundable(myProject, "Git: refresh current branches") {
      @Override public void run(@NotNull ProgressIndicator indicator) {
        assert ! mySoleUseControl.get();
        mySoleUseControl.set(true);
        try {
        Map<VirtualFile, GitBranch> currentBranches = new HashMap<VirtualFile, GitBranch>();
        for (VirtualFile root : roots) {
          try {
            GitBranch currentBranch = GitBranch.current(myProject, root);
            currentBranches.put(root, currentBranch);
            notifyListeners();
          } catch (VcsException e) {
            LOG.info("Exception while trying to get current branch for root " + root, e);
            // doing nothing - null will be set to myCurrentBranchName
          }
        }
        synchronized (myCurrentBranchesLock) {
          myCurrentBranches = currentBranches;
        }
        } finally {
          mySoleUseControl.set(false);
        }
      }
    };
    GitVcs.runInBackground(task);
  }

  private void notifyListeners() {
    myListeners.getMulticaster().branchConfigurationChanged();
  }

}

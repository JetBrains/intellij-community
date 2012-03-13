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
package git4idea.repo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import git4idea.GitUtil;
import git4idea.PlatformFacade;
import git4idea.roots.GitRootScanner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * GitRepositoryManager initializes and stores {@link GitRepository GitRepositories} for Git roots defined in the project.
 * @author Kirill Likhodedov
 */
public final class GitRepositoryManager extends AbstractProjectComponent implements Disposable {

  private static final Logger LOG = Logger.getInstance(GitRepositoryManager.class);

  @NotNull private final AbstractVcs myVcs;
  @NotNull private final ProjectLevelVcsManager myVcsManager;

  @NotNull private final Map<VirtualFile, GitRepository> myRepositories = new HashMap<VirtualFile, GitRepository>();
  @NotNull private final Set<GitRepositoryChangeListener> myListeners = new HashSet<GitRepositoryChangeListener>();

  @NotNull private final ReentrantReadWriteLock REPO_LOCK = new ReentrantReadWriteLock();
  private GitRootScanner myRootScanner;

  @Nullable
  public static GitRepositoryManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GitRepositoryManager.class);
  }

  public GitRepositoryManager(@NotNull Project project, @NotNull PlatformFacade platformFacade) {
    super(project);
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myVcs = platformFacade.getVcs(myProject);
  }

  @Override
  public void initComponent() {
    Disposer.register(myProject, this);
    myRootScanner = new GitRootScanner(myProject, new DumbAwareRunnable() {
      @Override
      public void run() {
        updateRepositoriesCollection();
      }
    });
    Disposer.register(this, myRootScanner);
  }

  @Override
  public void dispose() {
    try {
      REPO_LOCK.writeLock().lock();
      myRepositories.clear();
      myListeners.clear();
    }
    finally {
      REPO_LOCK.writeLock().unlock();
    }
  }

  /**
   * Returns the {@link GitRepository} which tracks the Git repository located in the given directory,
   * or {@code null} if the given file is not a Git root known to this {@link Project}.
   */
  @Nullable
  public GitRepository getRepositoryForRoot(@Nullable VirtualFile root) {
    if (root == null) {
      return null;
    }
    try {
      REPO_LOCK.readLock().lock();
      return myRepositories.get(root);
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }
  }

  /**
   * Returns the {@link GitRepository} which the given file belongs to, or {@code null} if the file is not under any Git repository.
   */
  @Nullable
  public GitRepository getRepositoryForFile(@NotNull VirtualFile file) {
    final VirtualFile vcsRoot = myVcsManager.getVcsRootFor(file);
    if (vcsRoot == null) { return null; }
    return getRepositoryForRoot(vcsRoot);
  }

  /**
   * @return all repositories tracked by the manager.
   */
  @NotNull
  public List<GitRepository> getRepositories() {
    try {
      REPO_LOCK.readLock().lock();
      return GitUtil.sortRepositories(myRepositories.values());
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }
  }

  public boolean moreThanOneRoot() {
    return myRepositories.values().size() > 1;
  }

  /**
   * Adds the listener to all existing repositories AND all future repositories.
   * I.e. if a new GitRepository is be created via this GitRepositoryManager, the listener will be added to the repository.
   */
  public void addListenerToAllRepositories(@NotNull GitRepositoryChangeListener listener) {
    myListeners.add(listener);
    for (GitRepository repo : getRepositories()) {
      repo.addListener(listener);
    }
  }

  /**
   * Synchronously updates the specified information about Git repository under the given root.
   * @param root   root directory of the Git repository.
   * @param topics TrackedTopics that are to be updated.
   */
  public void updateRepository(VirtualFile root, GitRepository.TrackedTopic... topics) {
    GitRepository repo = getRepositoryForRoot(root);
    if (repo != null) {
      repo.update(topics);
    }
  }

  public void updateAllRepositories(GitRepository.TrackedTopic... topics) {
    for (VirtualFile root : myRepositories.keySet()) {
      updateRepository(root, topics);
    }
  }

  // note: we are not calling this method during the project startup - it is called anyway by the GitRootTracker
  private void updateRepositoriesCollection() {
    try {
      REPO_LOCK.writeLock().lock();
      final VirtualFile[] roots = myVcsManager.getRootsUnderVcs(myVcs);
      // remove repositories that are not in the roots anymore
      for (Iterator<Map.Entry<VirtualFile, GitRepository>> iterator = myRepositories.entrySet().iterator(); iterator.hasNext(); ) {
        if (!ArrayUtil.contains(iterator.next().getValue().getRoot(), roots)) {
          iterator.remove();
        }
      }
      // add GitRepositories for all roots that don't have correspondent GitRepositories yet.
      for (VirtualFile root : roots) {
        if (!myRepositories.containsKey(root)) {
          if (gitRootOK(root)) {
            GitRepository repository = createGitRepository(root);
            myRepositories.put(root, repository);
          }
          else {
            LOG.info("Invalid Git root: " + root);
          }
        }
      }
    }
    finally {
        REPO_LOCK.writeLock().unlock();
    }
  }

  private static boolean gitRootOK(@NotNull VirtualFile root) {
    VirtualFile gitDir = root.findChild(".git");
    return gitDir != null && gitDir.exists();
  }

  private GitRepository createGitRepository(VirtualFile root) {
    GitRepository repository = GitRepository.getFullInstance(root, myProject, this);
    for (GitRepositoryChangeListener listener : myListeners) {
      repository.addListener(listener);
    }
    return repository;
  }

  @Override
  public String toString() {
    return "GitRepositoryManager{myRepositories: " + myRepositories + '}';
  }

}

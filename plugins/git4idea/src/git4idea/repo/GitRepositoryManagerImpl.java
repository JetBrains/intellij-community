/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.roots.GitRootScanner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Kirill Likhodedov
 */
public class GitRepositoryManagerImpl extends AbstractProjectComponent implements Disposable, GitRepositoryManager {

  private static final Logger LOG = Logger.getInstance(GitRepositoryManager.class);

  @NotNull private final AbstractVcs myVcs;
  @NotNull private final ProjectLevelVcsManager myVcsManager;

  @NotNull private final Map<VirtualFile, GitRepository> myRepositories = new HashMap<VirtualFile, GitRepository>();
  @NotNull private final Set<GitRepositoryChangeListener> myListeners = new HashSet<GitRepositoryChangeListener>();

  @NotNull private final ReentrantReadWriteLock REPO_LOCK = new ReentrantReadWriteLock();
  @NotNull private final GitPlatformFacade myPlatformFacade;

  public GitRepositoryManagerImpl(@NotNull Project project, @NotNull GitPlatformFacade platformFacade) {
    super(project);
    myPlatformFacade = platformFacade;
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myVcs = platformFacade.getVcs(myProject);
  }

  @Override
  public void initComponent() {
    Disposer.register(myProject, this);
    GitRootScanner rootScanner = new GitRootScanner(myProject, new DumbAwareRunnable() {
      @Override
      public void run() {
        updateRepositoriesCollection();
      }
    });
    Disposer.register(this, rootScanner);
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

  @Override
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

  @Override
  @Nullable
  public GitRepository getRepositoryForFile(@NotNull VirtualFile file) {
    final VcsRoot vcsRoot = myVcsManager.getVcsRootObjectFor(file);
    return getRepositoryForVcsRoot(vcsRoot, file.getPath());
  }

  @Override
  public GitRepository getRepositoryForFile(@NotNull FilePath file) {
    final VcsRoot vcsRoot = myVcsManager.getVcsRootObjectFor(file);
    return getRepositoryForVcsRoot(vcsRoot, file.getPath());
  }

  @Nullable
  private GitRepository getRepositoryForVcsRoot(VcsRoot vcsRoot, String filePath) {
    if (vcsRoot == null) {
      return null;
    }
    final AbstractVcs vcs = vcsRoot.getVcs();
    if (!myVcs.equals(vcs)) {
      if (vcs != null) {
        // if null, the file is just not under version control, nothing interesting;
        // otherwise log, because Git method is requested not for a Git-controlled file
        LOG.info(String.format("getRepositoryForFile returned non-Git (%s) root for file %s", vcs.getDisplayName(), filePath));
      }
      return null;
    }
    return getRepositoryForRoot(vcsRoot.getPath());
  }

  @Override
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

  @Override
  public boolean moreThanOneRoot() {
    return myRepositories.size() > 1;
  }

  @Override
  public void addListenerToAllRepositories(@NotNull GitRepositoryChangeListener listener) {
    myListeners.add(listener);
    for (GitRepository repo : getRepositories()) {
      repo.addListener(listener);
    }
  }

  @Override
  public void updateRepository(VirtualFile root) {
    GitRepository repo = getRepositoryForRoot(root);
    if (repo != null) {
      repo.update();
    }
  }

  @Override
  public void updateAllRepositories() {
    Map<VirtualFile, GitRepository> repositories;
    try {
      REPO_LOCK.readLock().lock();
      repositories = new HashMap<VirtualFile, GitRepository>(myRepositories);
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }

    for (VirtualFile root : repositories.keySet()) {
      updateRepository(root);
    }
  }

  // note: we are not calling this method during the project startup - it is called anyway by the GitRootTracker
  private void updateRepositoriesCollection() {
    Map<VirtualFile, GitRepository> repositories;
    try {
      REPO_LOCK.readLock().lock();
      repositories = new HashMap<VirtualFile, GitRepository>(myRepositories);
    }
    finally {
      REPO_LOCK.readLock().unlock();
    }

    final VirtualFile[] roots = myVcsManager.getRootsUnderVcs(myVcs);
      // remove repositories that are not in the roots anymore
      for (Iterator<Map.Entry<VirtualFile, GitRepository>> iterator = repositories.entrySet().iterator(); iterator.hasNext(); ) {
        if (!ArrayUtil.contains(iterator.next().getValue().getRoot(), roots)) {
          iterator.remove();
        }
      }
      // add GitRepositories for all roots that don't have correspondent GitRepositories yet.
      for (VirtualFile root : roots) {
        if (!repositories.containsKey(root)) {
          if (gitRootOK(root)) {
            try {
              GitRepository repository = createGitRepository(root);
              repositories.put(root, repository);
            }
            catch (GitRepoStateException e) {
              LOG.error("Couldn't initialize GitRepository in " + root.getPresentableUrl(), e);
            }
          }
          else {
            LOG.info("Invalid Git root: " + root);
          }
        }
      }

    REPO_LOCK.writeLock().lock();
    try {
      myRepositories.clear();
      myRepositories.putAll(repositories);
    }
    finally {
        REPO_LOCK.writeLock().unlock();
    }
  }

  private static boolean gitRootOK(@NotNull VirtualFile root) {
    VirtualFile gitDir = root.findChild(GitUtil.DOT_GIT);
    return gitDir != null && gitDir.exists();
  }

  private GitRepository createGitRepository(VirtualFile root) {
    GitRepository repository = GitRepositoryImpl.getFullInstance(root, myProject, myPlatformFacade, this);
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

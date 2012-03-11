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

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.messages.MessageBus;
import git4idea.PlatformFacade;
import git4idea.roots.GitRootProblemNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static git4idea.GitUtil.sortRepositories;

/**
 * GitRepositoryManager initializes and stores {@link GitRepository GitRepositories} for Git roots defined in the project.
 * @author Kirill Likhodedov
 */
public final class GitRepositoryManager extends AbstractProjectComponent implements Disposable {

  private static final Logger LOG = Logger.getInstance(GitRepositoryManager.class);

  private final @NotNull AbstractVcs myVcs;
  private final @NotNull ProjectLevelVcsManager myVcsManager;

  private final @NotNull Map<VirtualFile, GitRepository> myRepositories = new HashMap<VirtualFile, GitRepository>();
  private final @NotNull Set<GitRepositoryChangeListener> myListeners = new HashSet<GitRepositoryChangeListener>();

  private final @NotNull ReentrantReadWriteLock REPO_LOCK = new ReentrantReadWriteLock();

  private final @NotNull Object ROOT_SCAN_STUB_OBJECT = new Object();
  private final @NotNull QueueProcessor<Object> myRootScanQueue = new QueueProcessor<Object>(new Consumer<Object>() {
    @Override
    public void consume(Object o) {
      GitRootProblemNotifier.getInstance(myProject).rescanAndNotifyIfNeeded();
    }
  });

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
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new DumbAwareRunnable() {
      @Override
      public void run() {
        final MessageBus messageBus = myProject.getMessageBus();
        final MyRepositoryCreationDeletionListener rootChangeListener = new MyRepositoryCreationDeletionListener();
        messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, rootChangeListener);
        messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, rootChangeListener);
        updateRepositoriesCollection();
      }
    });
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
      return sortRepositories(myRepositories.values());
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

    myRootScanQueue.add(ROOT_SCAN_STUB_OBJECT);
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

  private class MyRepositoryCreationDeletionListener implements BulkFileListener, ModuleRootListener {
    @Override
    public void before(@NotNull List<? extends VFileEvent> events) {
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
      for (VFileEvent event : events) {
        VirtualFile file = event.getFile();
        if (file != null && file.getName().equalsIgnoreCase(".git") && file.isDirectory()) {
          updateRepositoriesCollection();
        }
      }
    }

    @Override
    public void beforeRootsChange(ModuleRootEvent event) {
    }

    @Override
    public void rootsChanged(ModuleRootEvent event) {
      updateRepositoriesCollection();
    }
  }
}

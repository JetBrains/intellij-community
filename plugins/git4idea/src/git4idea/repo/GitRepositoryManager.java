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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import git4idea.GitVcs;
import git4idea.vfs.GitRootsListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * GitRepositoryManager initializes and stores {@link GitRepository GitRepositories} for Git roots defined in the project.
 * @author Kirill Likhodedov
 */
public class GitRepositoryManager extends AbstractProjectComponent implements Disposable, GitRootsListener {

  private final GitVcs myVcs;
  private final ProjectLevelVcsManager myVcsManager;
  private final Map<VirtualFile, GitRepository> myRepositories = new HashMap<VirtualFile, GitRepository>();
  private final Set<GitRepositoryChangeListener> myListeners = new HashSet<GitRepositoryChangeListener>();

  public static GitRepositoryManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GitRepositoryManager.class);
  }

  public GitRepositoryManager(@NotNull Project project) {
    super(project);
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myVcs = GitVcs.getInstance(myProject);
    assert myVcs != null;
  }

  @Override
  public void initComponent() {
    myVcs.addGitRootsListener(this);
    Disposer.register(myProject, this);
  }

  @Override
  public void dispose() {
    myRepositories.clear();
    myListeners.clear();
  }

  /**
   * Returns the {@link GitRepository} which tracks the Git repository located in the given directory,
   * or {@code null} if the given file is not a Git root known to this {@link Project}.
   */
  @Nullable
  public GitRepository getRepositoryForRoot(@NotNull VirtualFile root) {
    return myRepositories.get(root);
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
  public Set<GitRepository> getRepositories() {
    return new HashSet<GitRepository>(myRepositories.values());
  }

  /**
   * Adds the listener to all existing repositories AND all future repositories.
   * I.e. if a new GitRepository is be created via this GitRepositoryManager, the listener will be added to the repository.
   */
  public void addListenerToAllRepositories(@NotNull GitRepositoryChangeListener listener) {
    myListeners.add(listener);
    for (GitRepository repo : myRepositories.values()) {
      repo.addListener(listener);
    }
  }

  /**
   * Asynchronously refreshes all {@link GitRepository GitRepositories}.
   */
  public void refreshAllRepositories() {
    for (GitRepository repository : myRepositories.values()) {
      repository.refresh();
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

  // note: we are not calling this method during the project startup - it is called anyway by the GitRootTracker
  @Override
  public void gitRootsChanged() {
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
        GitRepository repository = createGitRepository(root);
        myRepositories.put(root, repository);
      }
    }
  }

  private GitRepository createGitRepository(VirtualFile root) {
    GitRepository repository = new GitRepository(root, myProject);
    for (GitRepositoryChangeListener listener : myListeners) {
      repository.addListener(listener);
    }
    Disposer.register(this, repository);
    return repository;
  }

}

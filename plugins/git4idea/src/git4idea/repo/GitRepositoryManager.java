/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.VirtualFileHierarchicalComparator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.config.GitVcsSettings;
import git4idea.rebase.GitRebaseSpec;
import git4idea.ui.branch.GitMultiRootBranchConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class GitRepositoryManager extends AbstractRepositoryManager<GitRepository> {
  private static final Logger LOG = Logger.getInstance(GitRepositoryManager.class);

  public static final Comparator<GitRepository> DEPENDENCY_COMPARATOR =
    (repo1, repo2) -> - VirtualFileHierarchicalComparator.getInstance().compare(repo1.getRoot(), repo2.getRoot());

  @NotNull private final GitVcsSettings mySettings;
  @Nullable private volatile GitRebaseSpec myOngoingRebaseSpec;

  /**
   * @deprecated To remove in IDEA 2017. Use {@link #GitRepositoryManager(Project, VcsRepositoryManager)}.
   */
  @SuppressWarnings("UnusedParameters")
  @Deprecated
  public GitRepositoryManager(@NotNull Project project, @NotNull GitPlatformFacade platformFacade,
                              @NotNull VcsRepositoryManager vcsRepositoryManager) {
    this(project, vcsRepositoryManager);
  }

  public GitRepositoryManager(@NotNull Project project, @NotNull VcsRepositoryManager vcsRepositoryManager) {
    super(vcsRepositoryManager, GitVcs.getInstance(project), GitUtil.DOT_GIT);
    mySettings = GitVcsSettings.getInstance(project);
  }

  @NotNull
  public static GitRepositoryManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GitRepositoryManager.class);
  }

  @Override
  public boolean isSyncEnabled() {
    return mySettings.getSyncSetting() == DvcsSyncSettings.Value.SYNC && !new GitMultiRootBranchConfig(getRepositories()).diverged();
  }

  @NotNull
  @Override
  public List<GitRepository> getRepositories() {
    return getRepositories(GitRepository.class);
  }

  @Nullable
  public GitRebaseSpec getOngoingRebaseSpec() {
    GitRebaseSpec rebaseSpec = myOngoingRebaseSpec;
    return rebaseSpec != null && rebaseSpec.isValid() ? rebaseSpec : null;
  }

  public boolean hasOngoingRebase() {
    return getOngoingRebaseSpec() != null;
  }

  public void setOngoingRebaseSpec(@Nullable GitRebaseSpec ongoingRebaseSpec) {
    myOngoingRebaseSpec = ongoingRebaseSpec != null && ongoingRebaseSpec.isValid() ? ongoingRebaseSpec : null;
  }

  @NotNull
  public Collection<GitRepository> getDirectSubmodules(@NotNull GitRepository superProject) {
    Collection<GitSubmoduleInfo> modules = superProject.getSubmodules();
    return ContainerUtil.mapNotNull(modules, module -> {
      VirtualFile submoduleDir = superProject.getRoot().findFileByRelativePath(module.getPath());
      if (submoduleDir == null) {
        LOG.debug("submodule dir not found at declared path [" + module.getPath() + "] of root [" + superProject.getRoot() + "]");
        return null;
      }
      GitRepository repository = getRepositoryForRoot(submoduleDir);
      if (repository == null) {
        LOG.warn("Submodule not registered as a repository: " + submoduleDir);
      }
      return repository;
    });
  }

  /**
   * <p>Sorts repositories "by dependency",
   * which means that if one repository "depends" on the other, it should be updated or pushed first.</p>
   * <p>Currently submodule-dependency is the only one which is taken into account.</p>
   * <p>If repositories are independent of each other, they are sorted {@link DvcsUtil#REPOSITORY_COMPARATOR by path}.</p>
   */
  @NotNull
  public List<GitRepository> sortByDependency(@NotNull Collection<GitRepository> repositories) {
    return ContainerUtil.sorted(repositories, DEPENDENCY_COMPARATOR);
  }
}

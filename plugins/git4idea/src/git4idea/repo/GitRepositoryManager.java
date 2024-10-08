// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.MultiRootBranches;
import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.ui.VirtualFileHierarchicalComparator;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vfs.AsyncVfsEventsPostProcessor;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.config.GitVcsSettings;
import git4idea.rebase.GitRebaseSpec;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.intellij.openapi.progress.util.BackgroundTaskUtil.syncPublisher;

@Service(Service.Level.PROJECT)
public final class GitRepositoryManager extends AbstractRepositoryManager<GitRepository> {
  public static final Comparator<GitRepository> DEPENDENCY_COMPARATOR =
    (repo1, repo2) -> -VirtualFileHierarchicalComparator.getInstance().compare(repo1.getRoot(), repo2.getRoot());

  private final ExecutorService myUpdateExecutor =
    SequentialTaskExecutor.createSequentialApplicationPoolExecutor("GitRepositoryManager");

  private volatile @Nullable GitRebaseSpec myOngoingRebaseSpec;

  public GitRepositoryManager(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    super(project, GitVcs.getKey(), GitUtil.DOT_GIT);

    AsyncVfsEventsPostProcessor.getInstance().addListener(new GitUntrackedDirtyScopeListener(this), coroutineScope);
  }

  public static @NotNull GitRepositoryManager getInstance(@NotNull Project project) {
    return project.getService(GitRepositoryManager.class);
  }

  @Override
  public boolean isSyncEnabled() {
    return GitVcsSettings.getInstance(getVcs().getProject()).getSyncSetting() == DvcsSyncSettings.Value.SYNC && !MultiRootBranches.diverged(getRepositories());
  }

  @Override
  public @NotNull List<GitRepository> getRepositories() {
    return getRepositories(GitRepository.class);
  }

  @Override
  public boolean shouldProposeSyncControl() {
    return !thereAreSubmodulesInProject() && super.shouldProposeSyncControl();
  }

  private boolean thereAreSubmodulesInProject() {
    return getRepositories().stream().anyMatch(repo -> !repo.getSubmodules().isEmpty());
  }

  public @Nullable GitRebaseSpec getOngoingRebaseSpec() {
    GitRebaseSpec rebaseSpec = myOngoingRebaseSpec;
    return rebaseSpec != null && rebaseSpec.isValid() ? rebaseSpec : null;
  }

  public boolean hasOngoingRebase() {
    return getOngoingRebaseSpec() != null;
  }

  public void setOngoingRebaseSpec(@Nullable GitRebaseSpec ongoingRebaseSpec) {
    myOngoingRebaseSpec = ongoingRebaseSpec != null && ongoingRebaseSpec.isValid() ? ongoingRebaseSpec : null;
  }

  void notifyListenersAsync(@NotNull GitRepository repository,
                            @Nullable GitRepoInfo previousInfo,
                            @NotNull GitRepoInfo info) {
    myUpdateExecutor.execute(() -> {
      if (!Disposer.isDisposed(repository)) {
        syncPublisher(repository.getProject(), GitRepository.GIT_REPO_CHANGE).repositoryChanged(repository);

        if (previousInfo != null) {
          syncPublisher(repository.getProject(), GitRepository.GIT_REPO_STATE_CHANGE).repositoryChanged(repository, previousInfo, info);
        }
        else {
          syncPublisher(repository.getProject(), GitRepository.GIT_REPO_STATE_CHANGE).repositoryCreated(repository, info);
        }
      }
    });
  }

  /**
   * <p>Sorts repositories "by dependency",
   * which means that if one repository "depends" on the other, it should be updated or pushed first.</p>
   * <p>Currently submodule-dependency is the only one which is taken into account.</p>
   * <p>If repositories are independent of each other, they are sorted {@link DvcsUtil#REPOSITORY_COMPARATOR by path}.</p>
   */
  public @NotNull List<GitRepository> sortByDependency(@NotNull Collection<? extends GitRepository> repositories) {
    return ContainerUtil.sorted(repositories, DEPENDENCY_COMPARATOR);
  }
}

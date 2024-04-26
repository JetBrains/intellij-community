// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.branch.GitRebaseParams;
import git4idea.commands.Git;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.stash.GitChangesSaver;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.dvcs.DvcsUtil.getShortNames;

public class GitRebaseSpec {

  private static final Logger LOG = Logger.getInstance(GitRebaseSpec.class);

  private final @Nullable GitRebaseParams myParams;
  private final @NotNull Map<GitRepository, GitRebaseStatus> myStatuses;
  private final @NotNull Map<GitRepository, String> myInitialHeadPositions;
  private final @NotNull Map<GitRepository, String> myInitialBranchNames;
  private final @NotNull GitChangesSaver mySaver;
  private final boolean myShouldBeSaved;

  public GitRebaseSpec(@Nullable GitRebaseParams params,
                       @NotNull Map<GitRepository, GitRebaseStatus> statuses,
                       @NotNull Map<GitRepository, String> initialHeadPositions,
                       @NotNull Map<GitRepository, String> initialBranchNames,
                       @NotNull GitChangesSaver saver,
                       boolean shouldBeSaved) {
    myParams = params;
    myStatuses = statuses;
    myInitialHeadPositions = initialHeadPositions;
    myInitialBranchNames = initialBranchNames;
    mySaver = saver;
    myShouldBeSaved = shouldBeSaved;
  }

  public static @NotNull GitRebaseSpec forNewRebase(@NotNull Project project,
                                                    @NotNull GitRebaseParams params,
                                                    @NotNull Collection<? extends GitRepository> repositories,
                                                    @NotNull ProgressIndicator indicator) {
    GitUtil.updateRepositories(repositories);
    Map<GitRepository, String> initialHeadPositions = findInitialHeadPositions(repositories, params.getBranch());
    Map<GitRepository, String> initialBranchNames = findInitialBranchNames(repositories);
    Map<GitRepository, GitRebaseStatus> initialStatusMap = new TreeMap<>(DvcsUtil.REPOSITORY_COMPARATOR);
    for (GitRepository repository : repositories) {
      initialStatusMap.put(repository, GitRebaseStatus.notStarted());
    }
    return new GitRebaseSpec(params, initialStatusMap, initialHeadPositions, initialBranchNames, newSaver(project, indicator), true);
  }

  public static @Nullable GitRebaseSpec forResumeInSingleRepository(@NotNull Project project,
                                                                    @NotNull GitRepository repository,
                                                                    @NotNull ProgressIndicator indicator) {
    if (!repository.isRebaseInProgress()) return null;
    GitRebaseStatus suspended = new GitRebaseStatus(GitRebaseStatus.Type.SUSPENDED);
    return new GitRebaseSpec(null, Collections.singletonMap(repository, suspended),
                             Collections.emptyMap(), Collections.emptyMap(), newSaver(project, indicator), false);
  }

  public boolean isValid() {
    return singleOngoingRebase() && rebaseStatusesMatch();
  }

  public @NotNull GitChangesSaver getSaver() {
    return mySaver;
  }

  public @NotNull Collection<GitRepository> getAllRepositories() {
    return myStatuses.keySet();
  }

  public @Nullable GitRepository getOngoingRebase() {
    return ContainerUtil.getFirstItem(getOngoingRebases());
  }

  public @Nullable GitRebaseParams getParams() {
    return myParams;
  }

  public @NotNull Map<GitRepository, GitRebaseStatus> getStatuses() {
    return Collections.unmodifiableMap(myStatuses);
  }

  public @NotNull Map<GitRepository,String> getHeadPositionsToRollback() {
    return ContainerUtil.filter(myInitialHeadPositions, repository -> myStatuses.get(repository).getType() == GitRebaseStatus.Type.SUCCESS);
  }

  /**
   * Returns names of branches which were current at the moment of this GitRebaseSpec creation. <br/>
   * The map may contain null elements, if some repositories were in the detached HEAD state.
   */
  public @NotNull Map<GitRepository, String> getInitialBranchNames() {
    return myInitialBranchNames;
  }

  public @NotNull GitRebaseSpec cloneWithNewStatuses(@NotNull Map<GitRepository, GitRebaseStatus> statuses) {
    return new GitRebaseSpec(myParams, statuses, myInitialHeadPositions, myInitialBranchNames, mySaver, true);
  }

  public boolean shouldBeSaved() {
    return myShouldBeSaved;
  }

  /**
   * Returns repositories for which rebase is in progress, has failed and we want to retry, or didn't start yet. <br/>
   * It is guaranteed that if there is a rebase in progress (returned by {@link #getOngoingRebase()}, it will be the first in the list.
   */
  public @NotNull List<GitRepository> getIncompleteRepositories() {
    List<GitRepository> incompleteRepositories = new ArrayList<>();
    final GitRepository ongoingRebase = getOngoingRebase();
    if (ongoingRebase != null) incompleteRepositories.add(ongoingRebase);
    incompleteRepositories.addAll(DvcsUtil.sortRepositories(ContainerUtil.filter(myStatuses.keySet(),
                                                                                 repository -> !repository.equals(ongoingRebase) && myStatuses.get(repository).getType() != GitRebaseStatus.Type.SUCCESS)));
    return incompleteRepositories;
  }

  private static @NotNull GitChangesSaver newSaver(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    Git git = Git.getInstance();
    GitSaveChangesPolicy saveMethod = GitVcsSettings.getInstance(project).getSaveChangesPolicy();
    return GitChangesSaver.getSaver(project, git, indicator,
                                    VcsBundle.message("stash.changes.message", GitBundle.message("rebase.operation.name")), saveMethod);
  }

  private static @NotNull Map<GitRepository, String> findInitialHeadPositions(@NotNull Collection<? extends GitRepository> repositories,
                                                                              final @Nullable String branchToCheckout) {
    return ContainerUtil.map2Map(repositories, repository -> {
      String currentRevision = findCurrentRevision(repository, branchToCheckout);
      LOG.debug("Current revision in [" + repository.getRoot().getName() + "] is [" + currentRevision + "]");
      return Pair.create(repository, currentRevision);
    });
  }

  private static @Nullable String findCurrentRevision(@NotNull GitRepository repository, @Nullable String branchToCheckout) {
    if (branchToCheckout != null) {
      GitLocalBranch branch = repository.getBranches().findLocalBranch(branchToCheckout);
      if (branch != null) {
        Hash hash = repository.getBranches().getHash(branch);
        if (hash != null) {
          return hash.asString();
        }
        else {
          LOG.warn("The hash for branch [" + branchToCheckout + "] is not known!");
        }
      }
      else {
        LOG.warn("The branch [" + branchToCheckout + "] is not known!");
      }
    }
    return repository.getCurrentRevision();
  }

  private static @NotNull Map<GitRepository, String> findInitialBranchNames(@NotNull Collection<? extends GitRepository> repositories) {
    return ContainerUtil.map2Map(repositories, repository -> {
      String currentBranchName = repository.getCurrentBranchName();
      LOG.debug("Current branch in [" + repository.getRoot().getName() + "] is [" + currentBranchName + "]");
      return Pair.create(repository, currentBranchName);
    });
  }

  private @NotNull Collection<GitRepository> getOngoingRebases() {
    return ContainerUtil.filter(myStatuses.keySet(), repository -> myStatuses.get(repository).getType() == GitRebaseStatus.Type.SUSPENDED);
  }

  private boolean singleOngoingRebase() {
    Collection<GitRepository> ongoingRebases = getOngoingRebases();
    if (ongoingRebases.size() > 1) {
      LOG.warn("Invalid rebase spec: rebase is in progress in " + getShortNames(ongoingRebases));
      return false;
    }
    return true;
  }

  private boolean rebaseStatusesMatch() {
    for (GitRepository repository : myStatuses.keySet()) {
      GitRebaseStatus.Type savedStatus = myStatuses.get(repository).getType();
      if (repository.isRebaseInProgress() && savedStatus != GitRebaseStatus.Type.SUSPENDED) {
        LOG.warn("Invalid rebase spec: rebase is in progress in " +
                 DvcsUtil.getShortRepositoryName(repository) + ", but it is saved as " + savedStatus);
        return false;
      }
      else if (!repository.isRebaseInProgress() && savedStatus == GitRebaseStatus.Type.SUSPENDED) {
        LOG.warn("Invalid rebase spec: rebase is not in progress in " + DvcsUtil.getShortRepositoryName(repository));
        return false;
      }
    }
    return true;
  }

  @Override
  public @NonNls String toString() {
    String initialHeadPositions = StringUtil.join(myInitialHeadPositions.keySet(),
                                                  repository -> DvcsUtil.getShortRepositoryName(repository) + ": " + myInitialHeadPositions.get(repository), ", ");
    String statuses = StringUtil.join(myStatuses.keySet(),
                                      repository -> DvcsUtil.getShortRepositoryName(repository) + ": " + myStatuses.get(repository), ", ");
    return String.format("{Params: [%s].\nInitial positions: %s.\nStatuses: %s.\nSaver: %s}", myParams, initialHeadPositions, statuses, mySaver);
  }
}

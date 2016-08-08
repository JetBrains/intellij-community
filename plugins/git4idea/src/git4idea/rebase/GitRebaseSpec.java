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
package git4idea.rebase;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.branch.GitRebaseParams;
import git4idea.commands.Git;
import git4idea.repo.GitRepository;
import git4idea.stash.GitChangesSaver;
import git4idea.stash.GitStashChangesSaver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.dvcs.DvcsUtil.getShortNames;

public class GitRebaseSpec {

  private static final Logger LOG = Logger.getInstance(GitRebaseSpec.class);

  @Nullable private final GitRebaseParams myParams;
  @NotNull private final Map<GitRepository, GitRebaseStatus> myStatuses;
  @NotNull private final Map<GitRepository, String> myInitialHeadPositions;
  @NotNull private final Map<GitRepository, String> myInitialBranchNames;
  @NotNull private final GitChangesSaver mySaver;
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

  @NotNull
  public static GitRebaseSpec forNewRebase(@NotNull Project project,
                                           @NotNull GitRebaseParams params,
                                           @NotNull Collection<GitRepository> repositories,
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

  @Nullable
  public static GitRebaseSpec forResumeInSingleRepository(@NotNull Project project,
                                                          @NotNull GitRepository repository,
                                                          @NotNull ProgressIndicator indicator) {
    if (!repository.isRebaseInProgress()) return null;
    GitRebaseStatus suspended = new GitRebaseStatus(GitRebaseStatus.Type.SUSPENDED, Collections.<GitRebaseUtils.CommitInfo>emptyList());
    return new GitRebaseSpec(null, Collections.singletonMap(repository, suspended),
                             Collections.<GitRepository, String>emptyMap(), Collections.<GitRepository, String>emptyMap(), newSaver(project, indicator), false);
  }

  public boolean isValid() {
    return singleOngoingRebase() && rebaseStatusesMatch();
  }

  @NotNull
  public GitChangesSaver getSaver() {
    return mySaver;
  }

  @NotNull
  public Collection<GitRepository> getAllRepositories() {
    return myStatuses.keySet();
  }

  @Nullable
  public GitRepository getOngoingRebase() {
    return ContainerUtil.getFirstItem(getOngoingRebases());
  }

  @Nullable
  public GitRebaseParams getParams() {
    return myParams;
  }

  @NotNull
  public Map<GitRepository, GitRebaseStatus> getStatuses() {
    return Collections.unmodifiableMap(myStatuses);
  }

  @NotNull
  public Map<GitRepository,String> getHeadPositionsToRollback() {
    return ContainerUtil.filter(myInitialHeadPositions, new Condition<GitRepository>() {
      @Override
      public boolean value(GitRepository repository) {
        return myStatuses.get(repository).getType() == GitRebaseStatus.Type.SUCCESS;
      }
    });
  }

  /**
   * Returns names of branches which were current at the moment of this GitRebaseSpec creation. <br/>
   * The map may contain null elements, if some repositories were in the detached HEAD state.
   */
  @NotNull
  public Map<GitRepository, String> getInitialBranchNames() {
    return myInitialBranchNames;
  }

  @NotNull
  public GitRebaseSpec cloneWithNewStatuses(@NotNull Map<GitRepository, GitRebaseStatus> statuses) {
    return new GitRebaseSpec(myParams, statuses, myInitialHeadPositions, myInitialBranchNames, mySaver, true);
  }

  public boolean shouldBeSaved() {
    return myShouldBeSaved;
  }

  /**
   * Returns repositories for which rebase is in progress, has failed and we want to retry, or didn't start yet. <br/>
   * It is guaranteed that if there is a rebase in progress (returned by {@link #getOngoingRebase()}, it will be the first in the list.
   */
  @NotNull
  public List<GitRepository> getIncompleteRepositories() {
    List<GitRepository> incompleteRepositories = ContainerUtil.newArrayList();
    final GitRepository ongoingRebase = getOngoingRebase();
    if (ongoingRebase != null) incompleteRepositories.add(ongoingRebase);
    incompleteRepositories.addAll(DvcsUtil.sortRepositories(ContainerUtil.filter(myStatuses.keySet(), new Condition<GitRepository>() {
      @Override
      public boolean value(@NotNull GitRepository repository) {
        return !repository.equals(ongoingRebase) && myStatuses.get(repository).getType() != GitRebaseStatus.Type.SUCCESS;
      }
    })));
    return incompleteRepositories;
  }

  @NotNull
  private static GitStashChangesSaver newSaver(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    Git git = ServiceManager.getService(Git.class);
    return new GitStashChangesSaver(project, git, indicator, "Uncommitted changes before rebase");
  }

  @NotNull
  private static Map<GitRepository, String> findInitialHeadPositions(@NotNull Collection<GitRepository> repositories,
                                                                     @Nullable final String branchToCheckout) {
    return ContainerUtil.map2Map(repositories, new Function<GitRepository, Pair<GitRepository, String>>() {
      @Override
      public Pair<GitRepository, String> fun(@NotNull GitRepository repository) {
        String currentRevision = findCurrentRevision(repository, branchToCheckout);
        LOG.debug("Current revision in [" + repository.getRoot().getName() + "] is [" + currentRevision + "]");
        return Pair.create(repository, currentRevision);
      }
    });
  }

  @Nullable
  private static String findCurrentRevision(@NotNull GitRepository repository, @Nullable String branchToCheckout) {
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

  @NotNull
  private static Map<GitRepository, String> findInitialBranchNames(@NotNull Collection<GitRepository> repositories) {
    return ContainerUtil.map2Map(repositories, new Function<GitRepository, Pair<GitRepository, String>>() {
      @Override
      public Pair<GitRepository, String> fun(@NotNull GitRepository repository) {
        String currentBranchName = repository.getCurrentBranchName();
        LOG.debug("Current branch in [" + repository.getRoot().getName() + "] is [" + currentBranchName + "]");
        return Pair.create(repository, currentBranchName);
      }
    });
  }

  @NotNull
  private Collection<GitRepository> getOngoingRebases() {
    return ContainerUtil.filter(myStatuses.keySet(), new Condition<GitRepository>() {
      @Override
      public boolean value(@NotNull GitRepository repository) {
        return myStatuses.get(repository).getType() == GitRebaseStatus.Type.SUSPENDED;
      }
    });
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
  public String toString() {
    String initialHeadPositions = StringUtil.join(myInitialHeadPositions.keySet(), new Function<GitRepository, String>() {
      @Override
      public String fun(@NotNull GitRepository repository) {
        return DvcsUtil.getShortRepositoryName(repository) + ": " + myInitialHeadPositions.get(repository);
      }
    }, ", ");
    String statuses = StringUtil.join(myStatuses.keySet(), new Function<GitRepository, String>() {
      @Override
      public String fun(GitRepository repository) {
        return DvcsUtil.getShortRepositoryName(repository) + ": " + myStatuses.get(repository);
      }
    }, ", ");
    return String.format("{Params: [%s].\nInitial positions: %s.\nStatuses: %s.\nSaver: %s}", myParams, initialHeadPositions, statuses, mySaver);
  }
}

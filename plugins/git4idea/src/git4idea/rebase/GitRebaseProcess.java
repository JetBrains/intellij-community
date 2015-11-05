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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.branch.GitRebaseParams;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitUntrackedFilesOverwrittenByOperationDetector;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.stash.GitChangesSaver;
import git4idea.stash.GitStashChangesSaver;
import git4idea.util.GitFreezingProcess;
import git4idea.util.GitUntrackedFilesHelper;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.*;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.openapi.vfs.VfsUtilCore.toVirtualFileArray;
import static git4idea.GitUtil.getRootsFromRepositories;
import static git4idea.GitUtil.mention;
import static java.util.Collections.singleton;

public class GitRebaseProcess {

  private static final Logger LOG = Logger.getInstance(GitRebaseProcess.class);

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final ChangeListManager myChangeListManager;
  @NotNull private final VcsNotifier myNotifier;
  @NotNull private final GitPlatformFacade myFacade;

  @NotNull private final List<GitRepository> myAllRepositories;
  @NotNull private final GitRebaseParams myParams;
  @NotNull private final GitChangesSaver mySaver;
  @NotNull private final Map<GitRepository, SuccessType> mySuccessfulRepositories;
  @NotNull private final Map<GitRepository, String> myInitialHeadPositions;

  @NotNull private final MultiMap<GitRepository, GitRebaseUtils.CommitInfo> mySkippedCommits;

  protected GitRebaseProcess(@NotNull Project project,
                             @NotNull List<GitRepository> repositories,
                             @NotNull GitRebaseParams params,
                             @NotNull ProgressIndicator indicator) {
    this(project, repositories, params, newSaver(project, indicator),
         MultiMap.<GitRepository, GitRebaseUtils.CommitInfo>create(), Collections.<GitRepository, SuccessType>emptyMap());
  }

  private GitRebaseProcess(@NotNull Project project,
                           @NotNull List<GitRepository> allRepositories,
                           @NotNull GitRebaseParams params,
                           @NotNull GitChangesSaver saver,
                           @NotNull MultiMap<GitRepository, GitRebaseUtils.CommitInfo> skippedCommits,
                           @NotNull Map<GitRepository, SuccessType> successfulRepositories) {
    myProject = project;
    myAllRepositories = allRepositories;
    myParams = params;
    mySaver = saver;
    mySuccessfulRepositories = successfulRepositories;

    myGit = ServiceManager.getService(Git.class);
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myNotifier = VcsNotifier.getInstance(myProject);
    myFacade = ServiceManager.getService(GitPlatformFacade.class);

    myInitialHeadPositions = readInitialHeadPositions(myAllRepositories);
    mySkippedCommits = skippedCommits;
  }

  void rebase() {
    new GitFreezingProcess(myProject, myFacade, "rebase", new Runnable() {
      public void run() {
        doRebase();
      }
    }).execute();
  }

  void abort(@Nullable GitRepository repositoryToAbort,
             @NotNull final Collection<GitRepository> successfulRepositories,
             @NotNull ProgressIndicator indicator) {
    new GitAbortRebaseProcess(myProject, repositoryToAbort, ContainerUtil.filter(myInitialHeadPositions, new Condition<GitRepository>() {
      @Override
      public boolean value(GitRepository repository) {
        return successfulRepositories.contains(repository);
      }
    }), indicator, mySaver).abortWithConfirmation();
  }

  void retry(final boolean continueRebase) {
    new GitFreezingProcess(myProject, myFacade, "rebase", new Runnable() {
      public void run() {
        GitRebaseParams params = continueRebase ? myParams.withMode(GitRebaseParams.Mode.CONTINUE) : myParams;
        new GitRebaseProcess(myProject, myAllRepositories, params, mySaver, mySkippedCommits, mySuccessfulRepositories).doRebase();
      }
    }).execute();
  }

  private void doRebase() {
    LOG.debug("Started rebase.");
    Map<GitRepository, SuccessType> successfulWithTypes = ContainerUtil.newLinkedHashMap(mySuccessfulRepositories);
    List<GitRepository> repositories = ContainerUtil.newArrayList(myAllRepositories);
    repositories.removeAll(successfulWithTypes.keySet());
    ListIterator<GitRepository> iterator = repositories.listIterator();

    AccessToken token = DvcsUtil.workingTreeChangeStarted(myProject);
    try {
      if (!saveDirtyRootsInitially(repositories)) return;

      GitRepository facedDirtyError = null;
      Map<GitRepository, GitRebaseParams.Mode> customModes = ContainerUtil.newLinkedHashMap();
      while (iterator.hasNext()) {
        GitRepository repository = iterator.next();
        VirtualFile root = repository.getRoot();
        String repoName = getShortRepositoryName(repository);
        LOG.debug("Rebase iteration. Root: " + repoName);
        Collection<GitRepository> successful = successfulWithTypes.keySet();

        GitRebaseProblemDetector rebaseDetector = new GitRebaseProblemDetector();
        GitUntrackedFilesOverwrittenByOperationDetector untrackedDetector = new GitUntrackedFilesOverwrittenByOperationDetector(root);
        GitRebaseLineListener progressListener = new GitRebaseLineListener();

        GitRebaseParams.Mode customMode = customModes.get(repository);
        GitRebaseParams rebaseParams = customMode != null ? myParams.withMode(customMode) : myParams;
        GitCommandResult result = myGit.rebase(repository, rebaseParams, rebaseDetector, untrackedDetector, progressListener);
        boolean nonStandardMode = rebaseParams.getMode() != GitRebaseParams.Mode.STANDARD;
        boolean somethingRebased = nonStandardMode || progressListener.getResult().current > 1;

        if (result.success()) {
          LOG.debug("Successfully rebased " + repoName);
          successfulWithTypes.put(repository, SuccessType.fromOutput(result.getOutput()));
        }
        else if (rebaseDetector.isDirtyTree() && !nonStandardMode && repository != facedDirtyError) {
          // if the initial dirty tree check doesn't find all local changes, we are still ready to stash-on-demand,
          // but only once per repository (if the error happens again, that means that the previous stash attempt failed for some reason),
          // and not in the case of --continue (where all local changes are expected to be committed) or --skip.

          LOG.debug("Dirty tree detected in " + repoName);
          String saveError = saveLocalChanges(singleton(repository.getRoot()));
          if (saveError == null) {
            iterator.previous(); // try same repository again
            facedDirtyError = repository;
          }
          else {
            LOG.warn("Couldn't " + mySaver.getOperationName() + " root " + repository.getRoot() + ": " + saveError);
            showFatalError(saveError, repository, somethingRebased, successful);
            return;
          }
        }
        else if (rebaseDetector.isMergeConflict()) {
          LOG.debug("Merge conflict in " + repoName);
          boolean allResolved = showConflictResolver(repository, false);
          if (allResolved) {
            iterator.previous(); // continue with the same repository
            customModes.put(repository, GitRebaseParams.Mode.CONTINUE);
          }
          else {
            notifyNotAllConflictsResolved(repository, successful);
            return;
          }
        }
        else if (rebaseDetector.isNoChangeError()) {
          LOG.info("'No changes' situation detected in " + repoName);
          mySkippedCommits.putValue(repository, GitRebaseUtils.getCurrentRebaseCommit(root));
          iterator.previous();
          customModes.put(repository, GitRebaseParams.Mode.SKIP);
        }
        else if (untrackedDetector.wasMessageDetected()) {
          LOG.debug("Untracked files detected in " + repoName);
          showUntrackedFilesError(untrackedDetector.getRelativeFilePaths(), repository, somethingRebased, successful);
          return;
        }
        else {
          LOG.info("Error rebasing root " + repoName + ": " + result.getErrorOutputAsJoinedString());
          showFatalError(result.getErrorOutputAsHtmlString(), repository, somethingRebased, successful);
          return;
        }
      } // while

      LOG.debug("Rebase completed successfully.");
      mySaver.load();
    }
    finally {
      refresh(getRepositoriesToRefresh(successfulWithTypes, iterator.previous()));
      DvcsUtil.workingTreeChangeFinished(myProject, token);
    }
    notifySuccess(successfulWithTypes); // refresh _before_ showing the notification
  }

  @VisibleForTesting
  @NotNull
  protected Collection<GitRepository> getDirtyRoots(@NotNull Collection<GitRepository> repositories) {
    return findRootsWithLocalChanges(repositories);
  }

  @NotNull
  private static Map<GitRepository, String> readInitialHeadPositions(@NotNull Collection<GitRepository> repositories) {
    updateRepositoriesInfo(repositories);
    return ContainerUtil.map2Map(repositories, new Function<GitRepository, Pair<GitRepository, String>>() {
      @Override
      public Pair<GitRepository, String> fun(@NotNull GitRepository repository) {
        String currentRevision = repository.getCurrentRevision();
        LOG.debug("Current revision in [" + repository.getRoot().getName() + "] is [" + currentRevision + "]");
        return Pair.create(repository, currentRevision);
      }
    });
  }

  @NotNull
  private static Collection<GitRepository> getRepositoriesToRefresh(@NotNull final Map<GitRepository, SuccessType> successfulWithTypes,
                                                                    @NotNull GitRepository latestProcessed) {
    Collection<GitRepository> toRefresh = ContainerUtil.newHashSet(ContainerUtil.filter(successfulWithTypes.keySet(),
                                                                                        new Condition<GitRepository>() {
        @Override
        public boolean value(GitRepository repository) {
          return successfulWithTypes.get(repository) != SuccessType.UP_TO_DATE;
        }
      }));
    toRefresh.add(latestProcessed);
    return toRefresh;
  }

  private static void refresh(@NotNull Collection<GitRepository> repositories) {
    updateRepositoriesInfo(repositories);
    VfsUtil.markDirtyAndRefresh(false, true, false, toVirtualFileArray(getRootsFromRepositories(repositories)));
  }

  private static void updateRepositoriesInfo(@NotNull Collection<GitRepository> repositories) {
    for (GitRepository repository : repositories) {
      repository.update();
    }
  }

  @NotNull
  private static GitStashChangesSaver newSaver(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    Git git = ServiceManager.getService(Git.class);
    GitPlatformFacade facade = ServiceManager.getService(GitPlatformFacade.class);
    return new GitStashChangesSaver(project, facade, git, indicator, "Uncommitted changes before rebase");
  }

  private boolean saveDirtyRootsInitially(@NotNull List<GitRepository> repositories) {
    if (myParams.getMode() != GitRebaseParams.Mode.STANDARD) {
      LOG.debug("No need to pre-save dirty roots in the " + myParams.getMode() + " mode");
      return true;
    }
    Collection<VirtualFile> rootsToSave = getRootsFromRepositories(getDirtyRoots(repositories));
    String error = saveLocalChanges(rootsToSave);
    if (error != null) {
      myNotifier.notifyError("Rebase not Started", error);
      return false;
    }
    return true;
  }

  @Nullable
  private String saveLocalChanges(@NotNull Collection<VirtualFile> rootsToSave) {
    try {
      mySaver.saveLocalChanges(rootsToSave);
      return null;
    }
    catch (VcsException e) {
      LOG.warn(e);
      return "Couldn't " + mySaver.getSaverName() + " local uncommitted changes:<br/>" + e.getMessage();
    }
  }

  private Collection<GitRepository> findRootsWithLocalChanges(@NotNull Collection<GitRepository> repositories) {
    return ContainerUtil.filter(repositories, new Condition<GitRepository>() {
      @Override
      public boolean value(GitRepository repository) {
        return myChangeListManager.haveChangesUnder(repository.getRoot()) != ThreeState.NO;
      }
    });
  }

  private void notifySuccess(@NotNull Map<GitRepository, SuccessType> successful) {
    String rebasedBranch = getCommonCurrentBranchNameIfAllTheSame(myAllRepositories);
    SuccessType commonType = getItemIfAllTheSame(successful.values(), SuccessType.REBASED);
    String message = commonType.formatMessage(rebasedBranch, myParams.getBase());
    message += mentionSkippedCommits();
    myNotifier.notifyMinorInfo("Rebase Successful", message, new NotificationListener.Adapter() {
      @Override
      protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
        handlePossibleCommitLinks(e.getDescription());
      }
    });
  }

  @Nullable
  private static String getCommonCurrentBranchNameIfAllTheSame(@NotNull Collection<GitRepository> repositories) {
    return getItemIfAllTheSame(ContainerUtil.map(repositories, new Function<GitRepository, String>() {
      @Override
      public String fun(@NotNull GitRepository repository) {
        return repository.getCurrentBranchName();
      }
    }), null);
  }

  @Contract("_, !null -> !null")
  private static <T> T getItemIfAllTheSame(@NotNull Collection<T> collection, @Nullable T defaultItem) {
    return ContainerUtil.newHashSet(collection).size() == 1 ? ContainerUtil.getFirstItem(collection) : defaultItem;
  }

  private void notifyNotAllConflictsResolved(@NotNull GitRepository conflictingRepository, @NotNull Collection<GitRepository> successful) {
    String description = "You have to <a href='resolve'>resolve</a> the conflicts and <a href='continue'>continue</a> rebase.<br/>" +
                         "If you want to start from the beginning, you can <a href='abort'>abort</a> rebase.";
    description += GitRebaseUtils.mentionLocalChangesRemainingInStash(mySaver);
    myNotifier.notifyImportantWarning("Rebase Suspended", description,
                                      new RebaseNotificationListener(conflictingRepository, true, successful));
  }

  private boolean showConflictResolver(@NotNull GitRepository conflicting, boolean calledFromNotification) {
    GitConflictResolver.Params params = new GitConflictResolver.Params().setReverse(true);
    RebaseConflictResolver conflictResolver = new RebaseConflictResolver(myProject, myGit, myFacade, conflicting, params,
                                                                         calledFromNotification);
    return conflictResolver.merge();
  }

  private void showFatalError(@NotNull final String error,
                              @NotNull final GitRepository currentRepository,
                              boolean somethingWasRebased,
                              @NotNull final Collection<GitRepository> successful) {
    String description = "Rebase failed with error" + mention(currentRepository) + ": " + error + "<br/>" +
                         mentionRetryAndAbort(somethingWasRebased, successful) +
                         mentionSkippedCommits() +
                         GitRebaseUtils.mentionLocalChangesRemainingInStash(mySaver);
    myNotifier.notifyError("Rebase Failed", description,
                           new RebaseNotificationListener(currentRepository, somethingWasRebased, successful));
  }

  private void showUntrackedFilesError(@NotNull Set<String> untrackedPaths,
                                       @NotNull GitRepository currentRepository,
                                       boolean somethingWasRebased,
                                       @NotNull Collection<GitRepository> successful) {
    String message = GitUntrackedFilesHelper.createUntrackedFilesOverwrittenDescription("rebase", true) +
                     mentionRetryAndAbort(somethingWasRebased, successful) +
                     mentionSkippedCommits() +
                     GitRebaseUtils.mentionLocalChangesRemainingInStash(mySaver);
    GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(myProject, currentRepository.getRoot(), untrackedPaths, "rebase", message);
  }

  @NotNull
  private static String mentionRetryAndAbort(boolean somethingWasRebased, @NotNull Collection<GitRepository> successful) {
    return somethingWasRebased || !successful.isEmpty()
           ? "You can <a href='retry'>retry</a> or <a href='abort'>abort</a> rebase."
           : "<a href='retry'>Retry.</a>";
  }

  @NotNull
  private String mentionSkippedCommits() {
    if (mySkippedCommits.isEmpty()) return "";
    String message = "<br/>";
    if (mySkippedCommits.values().size() == 1) {
      message += "The following commit was skipped during rebase:<br/>";
    }
    else {
      message += "The following commits were skipped during rebase:<br/>";
    }
    message += StringUtil.join(mySkippedCommits.values(), new Function<GitRebaseUtils.CommitInfo, String>() {
      @Override
      public String fun(@NotNull GitRebaseUtils.CommitInfo commitInfo) {
        String commitMessage = StringUtil.shortenPathWithEllipsis(commitInfo.subject, 72, true);
        String hash = commitInfo.revision.asString();
        String shortHash = DvcsUtil.getShortHash(commitInfo.revision.asString());
        return String.format("<a href='%s'>%s</a> %s", hash, shortHash, commitMessage);
      }
    }, "<br/>");
    return message;
  }

  private enum SuccessType {
    REBASED {
      @NotNull
      @Override
      public String formatMessage(@Nullable String currentBranch, @NotNull String baseBranch) {
        return "Rebased" + notNullize(currentBranch) + " on " + baseBranch;
      }
    },
    UP_TO_DATE {
      @NotNull
      @Override
      public String formatMessage(@Nullable String currentBranch, @NotNull String baseBranch) {
        return currentBranch != null ? currentBranch + " is up-to-date with " + baseBranch : "Up-to-date with " + baseBranch;
      }
    },
    FAST_FORWARDED {
      @NotNull
      @Override
      public String formatMessage(@Nullable String currentBranch, @NotNull String baseBranch) {
        return "Fast-forwarded" + notNullize(currentBranch) + " to " + baseBranch;
      }
    };

    @NotNull
    private static String notNullize(@Nullable String currentBranch) {
      return currentBranch != null ? " " + currentBranch : "";
    }

    @NotNull
    abstract String formatMessage(@Nullable String currentBranch, @NotNull String baseBranch);

    @NotNull
    static SuccessType fromOutput(@NotNull List<String> output) {
      for (String line : output) {
        if (StringUtil.containsIgnoreCase(line, "Fast-forwarded")) {
          return FAST_FORWARDED;
        }
        if (StringUtil.containsIgnoreCase(line, "is up to date")) {
          return UP_TO_DATE;
        }
      }
      return REBASED;
    }
  }

  private class RebaseConflictResolver extends GitConflictResolver {
    private final boolean myCalledFromNotification;

    RebaseConflictResolver(@NotNull Project project,
                           @NotNull Git git,
                           @NotNull GitPlatformFacade platformFacade,
                           @NotNull GitRepository repository,
                           @NotNull Params params, boolean calledFromNotification) {
      super(project, git, platformFacade, singleton(repository.getRoot()), params);
      myCalledFromNotification = calledFromNotification;
    }

    @Override
    protected void notifyUnresolvedRemain() {
      // will be handled in the common notification
    }

    @CalledInBackground
    @Override
    protected boolean proceedAfterAllMerged() throws VcsException {
      if (myCalledFromNotification) retry(true);
      return true;
    }
  }

  private class RebaseNotificationListener extends NotificationListener.Adapter {
    @NotNull private final GitRepository myCurrentRepository;
    private final boolean mySomethingWasRebased;
    @NotNull private final Collection<GitRepository> mySuccessful;

    RebaseNotificationListener(@NotNull GitRepository currentRepository,
                               boolean somethingWasRebased,
                               @NotNull Collection<GitRepository> successful) {
      myCurrentRepository = currentRepository;
      mySomethingWasRebased = somethingWasRebased;
      mySuccessful = successful;
    }

    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull final HyperlinkEvent e) {
      final String href = e.getDescription();
      if ("abort".equals(href)) {
        new Task.Backgroundable(myProject, "Aborting Rebase...") {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            abort(mySomethingWasRebased ? myCurrentRepository : null, mySuccessful, indicator);
          }
        }.queue();
      }
      else if ("retry".equals(href) || "continue".equals(href)) {
        new Task.Backgroundable(myProject, "Rebasing...") {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            retry(mySomethingWasRebased);
          }
        }.queue();
      }
      else if ("resolve".equals(href)) {
        showConflictResolver(myCurrentRepository, true);
      }
      else if ("stash".equals(href)) {
        mySaver.showSavedChanges();
      }
      else {
        handlePossibleCommitLinks(href);
      }
    }
  }

  private void handlePossibleCommitLinks(@NotNull String href) {
    GitRepository repository = findRootBySkippedCommit(href);
    if (repository != null) {
      GitUtil.showSubmittedFiles(myProject, href, repository.getRoot(), true, false);
    }
  }

  @Nullable
  private GitRepository findRootBySkippedCommit(@NotNull final String hash) {
    return ContainerUtil.find(mySkippedCommits.keySet(), new Condition<GitRepository>() {
      @Override
      public boolean value(GitRepository repository) {
        return ContainerUtil.exists(mySkippedCommits.get(repository), new Condition<GitRebaseUtils.CommitInfo>() {
          @Override
          public boolean value(GitRebaseUtils.CommitInfo info) {
            return info.revision.asString().equals(hash);
          }
        });
      }
    });
  }
}

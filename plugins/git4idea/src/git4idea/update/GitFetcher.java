// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update;

import com.intellij.dvcs.MultiRootMessage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import git4idea.*;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandlerListener;
import git4idea.fetch.GitFetchSupport;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static git4idea.GitBranch.REFS_HEADS_PREFIX;
import static git4idea.GitBranch.REFS_REMOTES_PREFIX;
import static git4idea.GitNotificationIdsHolder.*;
import static git4idea.commands.GitAuthenticationListener.GIT_AUTHENTICATION_SUCCESS;

/**
 * @deprecated Use {@link GitFetchSupport}
 */
@Deprecated
public class GitFetcher {

  private static final Logger LOG = Logger.getInstance(GitFetcher.class);

  private final Project myProject;
  private final GitRepositoryManager myRepositoryManager;
  private final ProgressIndicator myProgressIndicator;
  private final boolean myFetchAll;
  private final GitVcs myVcs;

  private final Collection<Exception> myErrors = new ArrayList<>();

  /**
   * @param fetchAll Pass {@code true} to fetch all remotes and all branches (like {@code git fetch} without parameters does).
   *                 Pass {@code false} to fetch only the tracked branch of the current branch.
   */
  public GitFetcher(@NotNull Project project, @NotNull ProgressIndicator progressIndicator, boolean fetchAll) {
    myProject = project;
    myProgressIndicator = progressIndicator;
    myFetchAll = fetchAll;
    myRepositoryManager = GitUtil.getRepositoryManager(myProject);
    myVcs = GitVcs.getInstance(project);
  }

  /**
   * Invokes 'git fetch'.
   * @return true if fetch was successful, false in the case of error.
   * @deprecated Use {@link GitFetchSupport}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public GitFetchResult fetch(@NotNull GitRepository repository) {
    // TODO need to have a fair compound result here
    GitFetchResult fetchResult = myFetchAll ? fetchAll(repository) : fetchCurrentRemote(repository);
    repository.update();
    repository.getRepositoryFiles().refreshTagsFiles();
    return fetchResult;
  }

  /**
   * @deprecated Use {@link GitFetchSupport}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @NotNull
  public GitFetchResult fetch(@NotNull VirtualFile root, @NotNull String remoteName, @Nullable String branch) {
    GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    if (repository == null) {
      return logError("Repository can't be null for " + root, myRepositoryManager.toString());
    }
    GitRemote remote = GitUtil.findRemoteByName(repository, remoteName);
    if (remote == null) {
      return logError("Couldn't find remote with the name " + remoteName, null);
    }
    return fetchRemote(repository, remote, branch);
  }

  private static GitFetchResult logError(@NotNull String message, @Nullable String additionalInfo) {
    String addInfo = additionalInfo != null ? "\n" + additionalInfo : "";
    LOG.error(message + addInfo);
    return GitFetchResult.error(message);
  }

  @NotNull
  private GitFetchResult fetchCurrentRemote(@NotNull GitRepository repository) {
    FetchParams fetchParams = getFetchParams(repository);
    if (fetchParams.isError()) {
      return fetchParams.getError();
    }

    GitRemote remote = fetchParams.getRemote();
    return fetchRemote(repository, remote, null);
  }

  @NotNull
  private GitFetchResult fetchRemote(@NotNull GitRepository repository,
                                     @NotNull GitRemote remote,
                                     @Nullable String branch) {
    return fetchNatively(repository, remote, branch);
  }

  // leaving this unused method, because the wanted behavior can change again
  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  private GitFetchResult fetchCurrentBranch(@NotNull GitRepository repository) {
    FetchParams fetchParams = getFetchParams(repository);
    if (fetchParams.isError()) {
      return fetchParams.getError();
    }

    GitRemote remote = fetchParams.getRemote();
    String remoteBranch = fetchParams.getRemoteBranch().getNameForRemoteOperations();
    return fetchNatively(repository, remote, remoteBranch);
  }

  @NotNull
  private static FetchParams getFetchParams(@NotNull GitRepository repository) {
    GitLocalBranch currentBranch = repository.getCurrentBranch();
    if (currentBranch == null) {
      // fetching current branch is called from Update Project and Push, where branch tracking is pre-checked
      String message = "Current branch can't be null here. \nRepository: " + repository;
      LOG.error(message);
      return new FetchParams(GitFetchResult.error(new Exception(message)));
    }
    GitBranchTrackInfo trackInfo = GitBranchUtil.getTrackInfoForBranch(repository, currentBranch);
    if (trackInfo == null) {
      String message = "Tracked info is null for branch " + currentBranch + "\n Repository: " + repository;
      LOG.error(message);
      return new FetchParams(GitFetchResult.error(new Exception(message)));
    }

    GitRemote remote = trackInfo.getRemote();
    return new FetchParams(remote, trackInfo.getRemoteBranch());
  }

  @NotNull
  private static GitFetchResult fetchAll(@NotNull GitRepository repository) {
    GitFetchResult fetchResult = GitFetchResult.success();
    for (GitRemote remote : repository.getRemotes()) {
      String url = remote.getFirstUrl();
      if (url == null) {
        LOG.error("URL is null for remote " + remote.getName());
        continue;
      }
      GitFetchResult res = fetchNatively(repository, remote, null);
      res.addPruneInfo(fetchResult.getPrunedRefs());
      fetchResult = res;
      if (!fetchResult.isSuccess()) {
        break;
      }
    }
    return fetchResult;
  }

  @NotNull
  private static GitFetchResult fetchNatively(@NotNull GitRepository repository, @NotNull GitRemote remote, @Nullable String branch) {
    Git git = Git.getInstance();
    String[] additionalParams = branch != null ?
                                new String[]{ getFetchSpecForBranch(branch, remote.getName()) } :
                                ArrayUtilRt.EMPTY_STRING_ARRAY;

    GitFetchPruneDetector pruneDetector = new GitFetchPruneDetector();
    GitCommandResult result = git.fetch(repository, remote,
                                        Collections.singletonList(pruneDetector), additionalParams);

    GitFetchResult fetchResult;
    if (result.success()) {
      BackgroundTaskUtil.syncPublisher(repository.getProject(), GIT_AUTHENTICATION_SUCCESS).authenticationSucceeded(repository, remote);
      fetchResult = GitFetchResult.success();
    }
    else if (result.cancelled()) {
      fetchResult = GitFetchResult.cancel();
    }
    else {
      fetchResult = GitFetchResult.error(result.getErrorOutputAsJoinedString());
    }
    fetchResult.addPruneInfo(pruneDetector.getPrunedRefs());
    return fetchResult;
  }

  private static String getRidOfPrefixIfExists(String branch) {
    if (branch.startsWith(REFS_HEADS_PREFIX)) {
      return branch.substring(REFS_HEADS_PREFIX.length());
    }
    return branch;
  }

  @NotNull
  private static String getFetchSpecForBranch(@NotNull String branch, @NotNull String remoteName) {
    branch = getRidOfPrefixIfExists(branch);
    return REFS_HEADS_PREFIX + branch + ":" + REFS_REMOTES_PREFIX + remoteName + "/" + branch;
  }

  @NotNull
  public Collection<Exception> getErrors() {
    return myErrors;
  }

  public static void displayFetchResult(@NotNull Project project,
                                        @NotNull GitFetchResult result,
                                        @Nullable @NlsContexts.NotificationTitle String errorNotificationTitle,
                                        @NotNull Collection<? extends Exception> errors) {
    VcsNotifier notifier = VcsNotifier.getInstance(project);
    if (result.isSuccess()) {
      notifier.notifySuccess(FETCH_SUCCESS, "",
                             GitBundle.message("notification.content.fetched.successfully") + result.getAdditionalInfo());
    }
    else if (result.isCancelled()) {
      notifier.notifyMinorWarning(GitNotificationIdsHolder.FETCH_CANCELLED, GitBundle.message("notification.content.fetch.cancelled.by.user") + result.getAdditionalInfo());
    }
    else if (result.isNotAuthorized()) {
      if (errorNotificationTitle != null) {
        notifier.notifyError(FETCH_ERROR,
                             errorNotificationTitle,
                             GitBundle.message("notification.content.fetch.failed.couldn.t.authorize") + result.getAdditionalInfo());
      }
      else {
        notifier.notifyError(FETCH_ERROR,
                             GitBundle.message("notification.title.fetch.failed"),
                             GitBundle.message("notification.content.couldn.t.authorize") + result.getAdditionalInfo());
      }
    }
    else {
      VcsNotifier.getInstance(project)
        .notifyError(FETCH_ERROR, GitBundle.message("notification.title.fetch.failed"), result.getAdditionalInfo(), errors);
    }
  }

  /**
   * Fetches all specified roots.
   * Once a root has failed, stops and displays the notification.
   * If needed, displays the successful notification at the end.
   * @param roots                   roots to fetch.
   * @param errorNotificationTitle  if specified, this notification title will be used instead of the standard "Fetch failed".
   *                                Use this when fetch is a part of a compound process.
   * @param notifySuccess           if set to {@code true} successful notification will be displayed.
   * @return true if all fetches were successful, false if at least one fetch failed.
   * @deprecated Use {@link GitFetchSupport}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public boolean fetchRootsAndNotify(@NotNull Collection<? extends GitRepository> roots,
                                     @Nullable @NlsContexts.NotificationTitle String errorNotificationTitle,
                                     boolean notifySuccess) {
    MultiRootMessage additionalInfo = new MultiRootMessage(myProject, GitUtil.getRootsFromRepositories(roots), false, true);
    for (GitRepository repository : roots) {
      LOG.info("fetching " + repository);
      GitFetchResult result = fetch(repository);
      String ai = result.getAdditionalInfo();
      if (!StringUtil.isEmptyOrSpaces(ai)) {
        additionalInfo.append(repository.getRoot(), ai);
      }
      if (!result.isSuccess()) {
        Collection<Exception> errors = new ArrayList<>(getErrors());
        errors.addAll(result.getErrors());
        displayFetchResult(myProject, result, errorNotificationTitle, errors);
        return false;
      }
    }
    if (notifySuccess) {
      VcsNotifier.getInstance(myProject).notifySuccess(FETCH_SUCCESS, "", GitBundle.message("notification.content.fetched.successfully"));
    }

    if (!additionalInfo.asString().isEmpty()) {
      VcsNotifier.getInstance(myProject).notifyMinorInfo(FETCH_DETAILS, GitBundle.message("notification.title.fetch.details"), additionalInfo.asString());
    }

    return true;
  }

  private static class GitFetchPruneDetector implements GitLineHandlerListener {

    private static final Pattern PRUNE_PATTERN = Pattern.compile("\\s*x\\s*\\[deleted\\].*->\\s*(\\S*)");

    @NotNull private final Collection<String> myPrunedRefs = new ArrayList<>();

    @Override
    public void onLineAvailable(String line, Key outputType) {
      //  x [deleted]         (none)     -> origin/frmari
      Matcher matcher = PRUNE_PATTERN.matcher(line);
      if (matcher.matches()) {
        myPrunedRefs.add(matcher.group(1));
      }
    }

    @NotNull
    public Collection<String> getPrunedRefs() {
      return myPrunedRefs;
    }
  }

  private static class FetchParams {
    private GitRemote myRemote;
    private GitRemoteBranch myRemoteBranch;
    private GitFetchResult myError;

    FetchParams(GitFetchResult error) {
      myError = error;
    }

    FetchParams(GitRemote remote, GitRemoteBranch remoteBranch) {
      myRemote = remote;
      myRemoteBranch = remoteBranch;
    }

    boolean isError() {
      return myError != null;
    }

    public GitFetchResult getError() {
      return myError;
    }

    public GitRemote getRemote() {
      return myRemote;
    }

    public GitRemoteBranch getRemoteBranch() {
      return myRemoteBranch;
    }
  }
}

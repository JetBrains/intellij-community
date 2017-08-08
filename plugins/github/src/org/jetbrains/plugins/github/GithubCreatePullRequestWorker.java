/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.github;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.vcs.log.VcsCommitMetadata;
import git4idea.DialogManager;
import git4idea.GitCommit;
import git4idea.GitLocalBranch;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitCompareBranchesDialog;
import git4idea.update.GitFetchResult;
import git4idea.update.GitFetcher;
import git4idea.util.GitCommitCompareInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.api.data.GithubBranch;
import org.jetbrains.plugins.github.api.data.GithubPullRequest;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed;
import org.jetbrains.plugins.github.exceptions.GithubOperationCanceledException;
import org.jetbrains.plugins.github.ui.GithubSelectForkDialog;
import org.jetbrains.plugins.github.util.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class GithubCreatePullRequestWorker {
  private static final Logger LOG = GithubUtil.LOG;
  private static final String CANNOT_CREATE_PULL_REQUEST = "Can't Create Pull Request";

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final GitRepository myGitRepository;
  @NotNull private final GithubAuthDataHolder myAuthHolder;

  @NotNull private final GithubFullPath myPath;
  @NotNull private final String myRemoteName;
  @NotNull private final String myRemoteUrl;
  @NotNull private final String myCurrentBranch;

  @NotNull private GithubFullPath mySource;

  @NotNull private final List<ForkInfo> myForks;
  @Nullable private List<GithubFullPath> myAvailableForks;

  private GithubCreatePullRequestWorker(@NotNull Project project,
                                        @NotNull Git git,
                                        @NotNull GitRepository gitRepository,
                                        @NotNull GithubAuthDataHolder authHolder,
                                        @NotNull GithubFullPath path,
                                        @NotNull String remoteName,
                                        @NotNull String remoteUrl,
                                        @NotNull String currentBranch) {
    myProject = project;
    myGit = git;
    myGitRepository = gitRepository;
    myAuthHolder = authHolder;
    myPath = path;
    myRemoteName = remoteName;
    myRemoteUrl = remoteUrl;
    myCurrentBranch = currentBranch;

    myForks = new ArrayList<>();
  }

  @NotNull
  public String getCurrentBranch() {
    return myCurrentBranch;
  }

  @NotNull
  public List<ForkInfo> getForks() {
    return myForks;
  }

  @Nullable
  public static GithubCreatePullRequestWorker create(@NotNull final Project project, @Nullable final VirtualFile file) {
    return GithubUtil.computeValueInModal(project, "Loading data...", indicator -> {
      Git git = ServiceManager.getService(Git.class);

      GitRepository gitRepository = GithubUtil.getGitRepository(project, file);
      if (gitRepository == null) {
        GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Can't find git repository");
        return null;
      }
      gitRepository.update();

      Pair<GitRemote, String> remote = GithubUtil.findGithubRemote(gitRepository);
      if (remote == null) {
        GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Can't find GitHub remote");
        return null;
      }
      String remoteName = remote.getFirst().getName();
      String remoteUrl = remote.getSecond();

      GithubFullPath path = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remoteUrl);
      if (path == null) {
        GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Can't process remote: " + remoteUrl);
        return null;
      }

      GitLocalBranch currentBranch = gitRepository.getCurrentBranch();
      if (currentBranch == null) {
        GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "No current branch");
        return null;
      }

      GithubAuthDataHolder authHolder;
      try {
        authHolder = GithubUtil.getValidAuthDataHolderFromConfig(project, AuthLevel.LOGGED, indicator);
      }
      catch (IOException e) {
        GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, e);
        return null;
      }

      GithubCreatePullRequestWorker worker =
        new GithubCreatePullRequestWorker(project, git, gitRepository, authHolder, path, remoteName, remoteUrl, currentBranch.getName());

      try {
        worker.initForks(indicator);
      }
      catch (IOException e) {
        GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, e);
        return null;
      }

      return worker;
    });
  }

  private void initForks(@NotNull ProgressIndicator indicator) throws IOException {
    doLoadForksFromGithub(indicator);
    doLoadForksFromGit(indicator);
    doLoadForksFromSettings(indicator);
  }

  @Nullable
  private ForkInfo doAddFork(@NotNull GithubFullPath path,
                             @Nullable String remoteName,
                             @NotNull ProgressIndicator indicator) {
    for (ForkInfo fork : myForks) {
      if (fork.getPath().equals(path)) {
        if (fork.getRemoteName() == null && remoteName != null) {
          fork.setRemoteName(remoteName);
        }
        return fork;
      }
    }

    try {
      List<String> branches = loadBranches(path, indicator);
      String defaultBranch = doLoadDefaultBranch(path, indicator);

      ForkInfo fork = new ForkInfo(path, branches, defaultBranch);
      myForks.add(fork);
      if (remoteName != null) {
        fork.setRemoteName(remoteName);
      }
      return fork;
    }
    catch (IOException e) {
      GithubNotifications.showWarning(myProject, "Can't load branches for " + path.getFullName(), e);
      return null;
    }
  }

  @Nullable
  private ForkInfo doAddFork(@NotNull GithubRepo repo, @NotNull ProgressIndicator indicator) {
    GithubFullPath path = repo.getFullPath();
    for (ForkInfo fork : myForks) {
      if (fork.getPath().equals(path)) {
        return fork;
      }
    }

    try {
      List<String> branches = loadBranches(path, indicator);
      String defaultBranch = repo.getDefaultBranch();

      ForkInfo fork = new ForkInfo(path, branches, defaultBranch);
      myForks.add(fork);
      return fork;
    }
    catch (IOException e) {
      GithubNotifications.showWarning(myProject, "Can't load branches for " + path.getFullName(), e);
      return null;
    }
  }

  private void doLoadForksFromSettings(@NotNull ProgressIndicator indicator) throws IOException {
    GithubFullPath savedRepo = GithubProjectSettings.getInstance(myProject).getCreatePullRequestDefaultRepo();
    if (savedRepo != null) {
      doAddFork(savedRepo, null, indicator);
    }
  }

  private void doLoadForksFromGit(@NotNull ProgressIndicator indicator) {
    for (GitRemote remote : myGitRepository.getRemotes()) {
      for (String url : remote.getUrls()) {
        if (GithubUrlUtil.isGithubUrl(url)) {
          GithubFullPath path = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url);
          if (path != null) {
            doAddFork(path, remote.getName(), indicator);
            break;
          }
        }
      }
    }
  }

  private void doLoadForksFromGithub(@NotNull ProgressIndicator indicator) throws IOException {
    GithubRepoDetailed repo = GithubUtil.runTask(myProject, myAuthHolder, indicator, connection ->
      GithubApiUtil.getDetailedRepoInfo(connection, myPath.getUser(), myPath.getRepository()));

    doAddFork(repo, indicator);
    if (repo.getParent() != null) {
      doAddFork(repo.getParent(), indicator);
    }
    if (repo.getSource() != null) {
      doAddFork(repo.getSource(), indicator);
    }

    mySource = repo.getSource() == null ? repo.getFullPath() : repo.getSource().getFullPath();
  }

  @NotNull
  private List<String> loadBranches(@NotNull final GithubFullPath fork, @NotNull ProgressIndicator indicator) throws IOException {
    List<GithubBranch> branches = GithubUtil.runTask(myProject, myAuthHolder, indicator, connection ->
      GithubApiUtil.getRepoBranches(connection, fork.getUser(), fork.getRepository()));
    return ContainerUtil.map(branches, GithubBranch::getName);
  }

  @Nullable
  private String doLoadDefaultBranch(@NotNull final GithubFullPath fork, @NotNull ProgressIndicator indicator) throws IOException {
    GithubRepo repo = GithubUtil.runTask(myProject, myAuthHolder, indicator, connection ->
      GithubApiUtil.getDetailedRepoInfo(connection, fork.getUser(), fork.getRepository()));
    return repo.getDefaultBranch();
  }

  public void launchFetchRemote(@NotNull final ForkInfo fork) {
    if (fork.getRemoteName() == null) return;

    if (fork.getFetchTask() != null) return;
    synchronized (fork.LOCK) {
      if (fork.getFetchTask() != null) return;

      final MasterFutureTask<Void> task = new MasterFutureTask<>(() -> {
        doFetchRemote(fork);
        return null;
      });
      fork.setFetchTask(task);

      ApplicationManager.getApplication().executeOnPooledThread(task);
    }
  }

  public void launchLoadDiffInfo(@NotNull final BranchInfo branch) {
    if (branch.getForkInfo().getRemoteName() == null) return;

    if (branch.getDiffInfoTask() != null) return;
    synchronized (branch.LOCK) {
      if (branch.getDiffInfoTask() != null) return;

      launchFetchRemote(branch.getForkInfo());
      MasterFutureTask<Void> masterTask = branch.getForkInfo().getFetchTask();
      assert masterTask != null;

      final SlaveFutureTask<DiffInfo> task = new SlaveFutureTask<>(masterTask, () -> doLoadDiffInfo(branch));
      branch.setDiffInfoTask(task);

      ApplicationManager.getApplication().executeOnPooledThread(task);
    }
  }

  @Nullable
  public DiffInfo getDiffInfo(@NotNull final BranchInfo branch) throws IOException {
    if (branch.getForkInfo().getRemoteName() == null) return null;

    launchLoadDiffInfo(branch);

    assert branch.getDiffInfoTask() != null;
    try {
      return branch.getDiffInfoTask().get();
    }
    catch (InterruptedException e) {
      throw new GithubOperationCanceledException(e);
    }
    catch (ExecutionException e) {
      Throwable ex = e.getCause();
      if (ex instanceof VcsException) throw new IOException(ex);
      LOG.error(ex);
      return null;
    }
  }

  private boolean doFetchRemote(@NotNull ForkInfo fork) {
    if (fork.getRemoteName() == null) return false;

    GitFetchResult result =
      new GitFetcher(myProject, new EmptyProgressIndicator(), false).fetch(myGitRepository.getRoot(), fork.getRemoteName(), null);
    if (!result.isSuccess()) {
      GitFetcher.displayFetchResult(myProject, result, null, result.getErrors());
      return false;
    }
    return true;
  }

  @NotNull
  private DiffInfo doLoadDiffInfo(@NotNull final BranchInfo branch) throws VcsException {
    // TODO: make cancelable and abort old speculative requests (when git4idea will allow to do so)
    String currentBranch = myCurrentBranch;
    String targetBranch = branch.getForkInfo().getRemoteName() + "/" + branch.getRemoteName();

    List<GitCommit> commits1 = GitHistoryUtils.history(myProject, myGitRepository.getRoot(), ".." + targetBranch);
    List<GitCommit> commits2 = GitHistoryUtils.history(myProject, myGitRepository.getRoot(), targetBranch + "..");
    Collection<Change> diff = GitChangeUtils.getDiff(myProject, myGitRepository.getRoot(), targetBranch, myCurrentBranch, null);
    GitCommitCompareInfo info = new GitCommitCompareInfo(GitCommitCompareInfo.InfoType.BRANCH_TO_HEAD);
    info.put(myGitRepository, diff);
    info.put(myGitRepository, Couple.of(commits1, commits2));

    return new DiffInfo(info, currentBranch, targetBranch);
  }

  private void doConfigureRemote(@NotNull ForkInfo fork) {
    if (fork.getRemoteName() != null) return;

    GithubFullPath path = fork.getPath();
    String url = GithubUrlUtil.getCloneUrl(path);

    if (GithubUtil.addGithubRemote(myProject, myGitRepository, path.getUser(), url)) {
      fork.setRemoteName(path.getUser());
    }
  }

  public void configureRemote(@NotNull final ForkInfo fork) {
    GithubUtil.computeValueInModal(myProject, "Creating remote..", false, indicator -> {
      doConfigureRemote(fork);
    });
  }

  @NotNull
  public Couple<String> getDefaultDescriptionMessage(@NotNull final BranchInfo branch) {
    Couple<String> message = branch.getDefaultMessage();
    if (message != null) return message;

    if (branch.getForkInfo().getRemoteName() == null) {
      return getSimpleDefaultDescriptionMessage(branch);
    }

    return GithubUtil.computeValueInModal(myProject, "Collecting last commits...", true, indicator -> {
      String localBranch = myCurrentBranch;
      String targetBranch = branch.getForkInfo().getRemoteName() + "/" + branch.getRemoteName();
      try {
        List<VcsCommitMetadata> commits =
          GitHistoryUtils.readLastCommits(myProject, myGitRepository.getRoot(), localBranch, targetBranch);
        if (commits == null) return getSimpleDefaultDescriptionMessage(branch);

        VcsCommitMetadata localCommit = commits.get(0);
        VcsCommitMetadata targetCommit = commits.get(1);

        if (localCommit.getParents().contains(targetCommit.getId())) {
          return GithubUtil.getGithubLikeFormattedDescriptionMessage(localCommit.getFullMessage());
        }
        return getSimpleDefaultDescriptionMessage(branch);
      }
      catch (ProcessCanceledException e) {
        return getSimpleDefaultDescriptionMessage(branch);
      }
      catch (VcsException e) {
        GithubNotifications.showWarning(myProject, "Can't collect additional data", e);
        return getSimpleDefaultDescriptionMessage(branch);
      }
    });
  }

  @NotNull
  public Couple<String> getSimpleDefaultDescriptionMessage(@NotNull final BranchInfo branch) {
    Couple<String> message = Couple.of(myCurrentBranch, "");
    branch.setDefaultMessage(message);
    return message;
  }

  public boolean checkAction(@Nullable final BranchInfo branch) {
    if (branch == null) {
      GithubNotifications.showWarningDialog(myProject, CANNOT_CREATE_PULL_REQUEST, "Target branch is not selected");
      return false;
    }

    DiffInfo info;
    try {
      info = GithubUtil.computeValueInModalIO(myProject, "Collecting diff data...", indicator ->
        GithubUtil.runInterruptable(indicator, () -> getDiffInfo(branch)));
    }
    catch (IOException e) {
      GithubNotifications.showError(myProject, "Can't collect diff data", e);
      return true;
    }
    if (info == null) {
      return true;
    }

    ForkInfo fork = branch.getForkInfo();

    String localBranchName = "'" + myCurrentBranch + "'";
    String targetBranchName = "'" + fork.getRemoteName() + "/" + branch.getRemoteName() + "'";
    if (info.getInfo().getBranchToHeadCommits(myGitRepository).isEmpty()) {
      return GithubNotifications
        .showYesNoDialog(myProject, "Empty Pull Request",
                         "The branch " + localBranchName + " is fully merged to the branch " + targetBranchName + '\n' +
                         "Do you want to proceed anyway?");
    }
    if (!info.getInfo().getHeadToBranchCommits(myGitRepository).isEmpty()) {
      return GithubNotifications
        .showYesNoDialog(myProject, "Target Branch Is Not Fully Merged",
                         "The branch " + targetBranchName + " is not fully merged to the branch " + localBranchName + '\n' +
                         "Do you want to proceed anyway?");
    }

    return true;
  }

  public void createPullRequest(@NotNull final BranchInfo branch,
                                @NotNull final String title,
                                @NotNull final String description) {
    new Task.Backgroundable(myProject, "Creating Pull Request...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        LOG.info("Pushing current branch");
        indicator.setText("Pushing current branch...");
        GitCommandResult result = myGit.push(myGitRepository, myRemoteName, myRemoteUrl, myCurrentBranch, true);
        if (!result.success()) {
          GithubNotifications.showError(myProject, CANNOT_CREATE_PULL_REQUEST, "Push failed:<br/>" + result.getErrorOutputAsHtmlString());
          return;
        }

        LOG.info("Creating pull request");
        indicator.setText("Creating pull request...");
        GithubPullRequest request = doCreatePullRequest(indicator, branch, title, description);
        if (request == null) {
          return;
        }

        GithubNotifications.showInfoURL(myProject, "Successfully created pull request",
                                        "Pull request #" + request.getNumber(), request.getHtmlUrl());
      }
    }.queue();
  }

  @Nullable
  private GithubPullRequest doCreatePullRequest(@NotNull ProgressIndicator indicator,
                                                @NotNull final BranchInfo branch,
                                                @NotNull final String title,
                                                @NotNull final String description) {
    final GithubFullPath forkPath = branch.getForkInfo().getPath();

    final String head = myPath.getUser() + ":" + myCurrentBranch;
    final String base = branch.getRemoteName();

    try {
      return GithubUtil.runTask(myProject, myAuthHolder, indicator, connection ->
        GithubApiUtil.createPullRequest(connection, forkPath.getUser(), forkPath.getRepository(), title, description, head, base));
    }
    catch (IOException e) {
      GithubNotifications.showError(myProject, CANNOT_CREATE_PULL_REQUEST, e);
      return null;
    }
  }

  public void showDiffDialog(@Nullable final BranchInfo branch) {
    if (branch == null) {
      GithubNotifications.showWarningDialog(myProject, "Can't Show Diff", "Target branch is not selected");
      return;
    }

    DiffInfo info;
    try {
      info = GithubUtil.computeValueInModalIO(myProject, "Collecting diff data...", indicator ->
        GithubUtil.runInterruptable(indicator, () -> getDiffInfo(branch)));
    }
    catch (IOException e) {
      GithubNotifications.showError(myProject, "Can't collect diff data", e);
      return;
    }
    if (info == null) {
      GithubNotifications.showErrorDialog(myProject, "Can't Show Diff", "Can't collect diff data");
      return;
    }

    GitCompareBranchesDialog dialog =
      new GitCompareBranchesDialog(myProject, info.getTo(), info.getFrom(), info.getInfo(), myGitRepository, true);
    dialog.show();
  }

  @Nullable
  public ForkInfo showTargetDialog() {
    if (myAvailableForks == null) {
      try {
        myAvailableForks = GithubUtil.computeValueInModal(myProject, myCurrentBranch, indicator -> getAvailableForks(indicator));
      }
      catch (ProcessCanceledException ignore) {
      }
    }

    Convertor<String, ForkInfo> getForkPath = user ->
      GithubUtil.computeValueInModal(myProject, "Access to GitHub", indicator -> findRepositoryByUser(indicator, user));

    GithubSelectForkDialog dialog = new GithubSelectForkDialog(myProject, myAvailableForks, getForkPath);
    DialogManager.show(dialog);
    if (!dialog.isOK()) {
      return null;
    }
    return dialog.getPath();
  }

  @Nullable
  private List<GithubFullPath> getAvailableForks(@NotNull ProgressIndicator indicator) {
    try {
      List<GithubRepo> forks = GithubUtil.runTask(myProject, myAuthHolder, indicator, connection ->
        GithubApiUtil.getForks(connection, mySource.getUser(), mySource.getRepository())
      );
      List<GithubFullPath> forkPaths = ContainerUtil.map(forks, GithubRepo::getFullPath);
      if (!forkPaths.contains(mySource)) return ContainerUtil.append(forkPaths, mySource);
      return forkPaths;
    }
    catch (IOException e) {
      GithubNotifications.showWarning(myProject, "Can't load available forks", e);
      return null;
    }
  }

  @Nullable
  private ForkInfo findRepositoryByUser(@NotNull final ProgressIndicator indicator, @NotNull final String user) {
    for (ForkInfo fork : myForks) {
      if (StringUtil.equalsIgnoreCase(user, fork.getPath().getUser())) {
        return fork;
      }
    }

    try {
      GithubRepo repo = GithubUtil.runTask(myProject, myAuthHolder, indicator, connection -> {
        try {
          GithubRepoDetailed target = GithubApiUtil.getDetailedRepoInfo(connection, user, mySource.getRepository());
          if (target.getSource() != null && StringUtil.equals(target.getSource().getUserName(), mySource.getUser())) {
            return target;
          }
        }
        catch (IOException ignore) {
          // such repo may not exist
        }

        return GithubApiUtil.findForkByUser(connection, mySource.getUser(), mySource.getRepository(), user);
      });

      if (repo == null) return null;
      return doAddFork(repo, indicator);
    }
    catch (IOException e) {
      GithubNotifications.showError(myProject, "Can't find repository", e);
      return null;
    }
  }

  public static class ForkInfo {
    @NotNull public final Object LOCK = new Object();

    // initial loading
    @NotNull private final GithubFullPath myPath;

    @NotNull private final String myDefaultBranch;
    @NotNull private final List<BranchInfo> myBranches;

    @Nullable private String myRemoteName;
    private boolean myProposedToCreateRemote;

    @Nullable private MasterFutureTask<Void> myFetchTask;

    public ForkInfo(@NotNull GithubFullPath path, @NotNull List<String> branches, @Nullable String defaultBranch) {
      myPath = path;
      myDefaultBranch = defaultBranch == null ? "master" : defaultBranch;
      myBranches = new ArrayList<>();
      for (String branchName : branches) {
        myBranches.add(new BranchInfo(branchName, this));
      }
    }

    @NotNull
    public GithubFullPath getPath() {
      return myPath;
    }

    @Nullable
    public String getRemoteName() {
      return myRemoteName;
    }

    @NotNull
    public String getDefaultBranch() {
      return myDefaultBranch;
    }

    @NotNull
    public List<BranchInfo> getBranches() {
      return myBranches;
    }

    public void setRemoteName(@NotNull String remoteName) {
      myRemoteName = remoteName;
    }

    public boolean isProposedToCreateRemote() {
      return myProposedToCreateRemote;
    }

    public void setProposedToCreateRemote(boolean proposedToCreateRemote) {
      myProposedToCreateRemote = proposedToCreateRemote;
    }

    @Nullable
    public MasterFutureTask<Void> getFetchTask() {
      return myFetchTask;
    }

    public void setFetchTask(@NotNull MasterFutureTask<Void> fetchTask) {
      myFetchTask = fetchTask;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ForkInfo info = (ForkInfo)o;

      if (!myPath.equals(info.myPath)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myPath.hashCode();
    }

    @Override
    public String toString() {
      return myPath.getUser() + ":" + myPath.getRepository();
    }
  }

  public static class BranchInfo {
    @NotNull public final Object LOCK = new Object();

    @NotNull private final ForkInfo myForkInfo;
    @NotNull private final String myRemoteName;

    @Nullable private SlaveFutureTask<DiffInfo> myDiffInfoTask;

    @Nullable private Couple<String> myDefaultMessage;

    public BranchInfo(@NotNull String remoteName, @NotNull ForkInfo fork) {
      myRemoteName = remoteName;
      myForkInfo = fork;
    }

    @NotNull
    public ForkInfo getForkInfo() {
      return myForkInfo;
    }

    @NotNull
    public String getRemoteName() {
      return myRemoteName;
    }

    @Nullable
    public SlaveFutureTask<DiffInfo> getDiffInfoTask() {
      return myDiffInfoTask;
    }

    public void setDiffInfoTask(@NotNull SlaveFutureTask<DiffInfo> diffInfoTask) {
      myDiffInfoTask = diffInfoTask;
    }

    @Nullable
    public Couple<String> getDefaultMessage() {
      return myDefaultMessage;
    }

    public void setDefaultMessage(@NotNull Couple<String> message) {
      myDefaultMessage = message;
    }

    @Override
    public String toString() {
      return myRemoteName;
    }
  }

  public static class DiffInfo {
    @NotNull private final GitCommitCompareInfo myInfo;
    @NotNull private final String myFrom;
    @NotNull private final String myTo;

    private DiffInfo(@NotNull GitCommitCompareInfo info, @NotNull String from, @NotNull String to) {
      myInfo = info;
      myFrom = from; // HEAD
      myTo = to;     // BASE
    }

    @NotNull
    public GitCommitCompareInfo getInfo() {
      return myInfo;
    }

    @NotNull
    public String getFrom() {
      return myFrom;
    }

    @NotNull
    public String getTo() {
      return myTo;
    }
  }

  public static class SlaveFutureTask<T> extends FutureTask<T> {
    @NotNull private final MasterFutureTask myMaster;

    public SlaveFutureTask(@NotNull MasterFutureTask master, @NotNull Callable<T> callable) {
      super(callable);
      myMaster = master;
    }

    @Override
    public void run() {
      if (myMaster.isDone()) {
        super.run();
      }
      else {
        if (!myMaster.addSlave(this)) {
          super.run();
        }
      }
    }

    public T safeGet() {
      try {
        return super.get();
      }
      catch (InterruptedException | ExecutionException | CancellationException e) {
        return null;
      }
    }
  }

  public static class MasterFutureTask<T> extends FutureTask<T> {
    @NotNull private final Object LOCK = new Object();
    private boolean myDone = false;

    @Nullable private List<SlaveFutureTask> mySlaves;

    public MasterFutureTask(@NotNull Callable<T> callable) {
      super(callable);
    }

    boolean addSlave(@NotNull SlaveFutureTask slave) {
      if (isDone()) {
        return false;
      }
      else {
        synchronized (LOCK) {
          if (myDone) return false;
          if (mySlaves == null) mySlaves = new ArrayList<>();
          mySlaves.add(slave);
          return true;
        }
      }
    }

    @Override
    protected void done() {
      synchronized (LOCK) {
        myDone = true;
        if (mySlaves != null) {
          for (final SlaveFutureTask slave : mySlaves) {
            runSlave(slave);
          }
          mySlaves = null;
        }
      }
    }

    protected void runSlave(@NotNull final SlaveFutureTask slave) {
      ApplicationManager.getApplication().executeOnPooledThread(slave);
    }
  }
}

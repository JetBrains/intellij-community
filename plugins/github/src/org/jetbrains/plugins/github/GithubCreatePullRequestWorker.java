// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github;

import com.intellij.dvcs.ui.CompareBranchesDialog;
import com.intellij.dvcs.util.CommitCompareInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.vcs.log.VcsCommitMetadata;
import git4idea.DialogManager;
import git4idea.GitCommit;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitCompareBranchesHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GithubBranch;
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailed;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed;
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader;
import org.jetbrains.plugins.github.exceptions.GithubConfusingException;
import org.jetbrains.plugins.github.exceptions.GithubOperationCanceledException;
import org.jetbrains.plugins.github.i18n.GithubBundle;
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

import static git4idea.fetch.GitFetchSupport.fetchSupport;

public final class GithubCreatePullRequestWorker {
  private static final Logger LOG = GithubUtil.LOG;

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final GitRepository myGitRepository;
  @NotNull private final GithubApiRequestExecutor myExecutor;
  @NotNull private final GithubServerPath myServer;
  @NotNull private final GithubGitHelper myGitHelper;
  @NotNull private final ProgressManager myProgressManager;

  @NotNull private final GHRepositoryPath myPath;
  @NotNull private final String myRemoteName;
  @NotNull private final String myRemoteUrl;
  @NotNull private final String myCurrentBranch;

  @SuppressWarnings("NullableProblems")
  @NotNull private GHRepositoryPath mySource;

  @NotNull private final List<ForkInfo> myForks;
  @Nullable private List<GHRepositoryPath> myAvailableForks;

  private GithubCreatePullRequestWorker(@NotNull Project project,
                                        @NotNull Git git,
                                        @NotNull GitRepository gitRepository,
                                        @NotNull GithubApiRequestExecutor executor,
                                        @NotNull GithubServerPath server,
                                        @NotNull GithubGitHelper helper,
                                        @NotNull ProgressManager progressManager,
                                        @NotNull GHRepositoryPath path,
                                        @NotNull String remoteName,
                                        @NotNull String remoteUrl,
                                        @NotNull String currentBranch) {
    myProject = project;
    myGit = git;
    myGitRepository = gitRepository;
    myExecutor = executor;
    myServer = server;
    myGitHelper = helper;
    myProgressManager = progressManager;
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

  private void initForks(@NotNull ProgressIndicator indicator) throws IOException {
    doLoadForksFromGithub(indicator);
    doLoadForksFromGit(indicator);
    doLoadForksFromSettings(indicator);
  }

  private void doConfigureRemote(@NotNull ForkInfo fork) {
    if (fork.getRemoteName() != null) return;

    GHRepositoryPath path = fork.getPath();
    String url = myGitHelper.getRemoteUrl(myServer, path);

    try {
      myGit.addRemote(myGitRepository, path.getOwner(), url).throwOnError();
      myGitRepository.update();
      fork.setRemoteName(path.getOwner());
    }
    catch (VcsException e) {
      GithubNotifications.showError(myProject, GithubBundle.message("pull.request.cannot.add.remote"),
                                    GithubBundle.message("pull.request.create.add.remote.failed", url, e.getMessage()));
    }
  }

  private void doAddFork(@NotNull GHRepositoryPath path,
                         @Nullable String remoteName,
                         @NotNull ProgressIndicator indicator) {
    for (ForkInfo fork : myForks) {
      if (fork.getPath().equals(path)) {
        if (fork.getRemoteName() == null && remoteName != null) {
          fork.setRemoteName(remoteName);
        }
        return;
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
    }
    catch (IOException e) {
      GithubNotifications.showWarning(myProject, GithubBundle.message("pull.request.cannot.load.branches", path), e);
    }
  }

  @Nullable
  private ForkInfo doAddFork(@NotNull GithubRepo repo, @NotNull ProgressIndicator indicator) {
    GHRepositoryPath path = repo.getFullPath();
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
      GithubNotifications.showWarning(myProject, GithubBundle.message("pull.request.cannot.load.branches", path), e);
      return null;
    }
  }

  private void doLoadForksFromSettings(@NotNull ProgressIndicator indicator) {
    GHRepositoryPath savedRepo = GithubProjectSettings.getInstance(myProject).getCreatePullRequestDefaultRepo();
    if (savedRepo != null) {
      doAddFork(savedRepo, null, indicator);
    }
  }

  private void doLoadForksFromGit(@NotNull ProgressIndicator indicator) {
    for (GitRemote remote : myGitRepository.getRemotes()) {
      for (String url : remote.getUrls()) {
        if (myServer.matches(url)) {
          GHRepositoryPath path = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url);
          if (path != null) {
            doAddFork(path, remote.getName(), indicator);
            break;
          }
        }
      }
    }
  }

  private void doLoadForksFromGithub(@NotNull ProgressIndicator indicator) throws IOException {
    GithubRepoDetailed repo = myExecutor.execute(indicator,
                                                 GithubApiRequests.Repos.get(myServer, myPath.getOwner(), myPath.getRepository()));
    if (repo == null) throw new GithubConfusingException("Can't find github repo " + myPath.toString());

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
  private List<String> loadBranches(@NotNull final GHRepositoryPath fork, @NotNull ProgressIndicator indicator) throws IOException {
    List<GithubBranch> branches = GithubApiPagesLoader
      .loadAll(myExecutor, indicator, GithubApiRequests.Repos.Branches.pages(myServer, fork.getOwner(), fork.getRepository()));
    return ContainerUtil.map(branches, GithubBranch::getName);
  }

  @Nullable
  private String doLoadDefaultBranch(@NotNull final GHRepositoryPath fork, @NotNull ProgressIndicator indicator) throws IOException {
    GithubRepo repo = myExecutor.execute(indicator,
                                         GithubApiRequests.Repos.get(myServer, fork.getOwner(), fork.getRepository()));
    if (repo == null) throw new GithubConfusingException("Can't find github repo " + fork.toString());
    return repo.getDefaultBranch();
  }

  public void launchFetchRemote(@NotNull final ForkInfo fork) {
    if (fork.getRemoteName() == null) return;

    if (fork.getFetchTask() != null) return;
    synchronized (fork.LOCK) {
      if (fork.getFetchTask() != null) return;

      final MasterFutureTask<Void> task = new MasterFutureTask<>(() -> {
        BackgroundTaskUtil.runUnderDisposeAwareIndicator(myProject, () -> doFetchRemote(fork));
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

  private void doFetchRemote(@NotNull ForkInfo fork) {
    String remoteName = fork.getRemoteName();
    if (remoteName == null) return;
    GitRemote remote = GitUtil.findRemoteByName(myGitRepository, remoteName);
    if (remote == null) {
      LOG.warn("Couldn't find remote " + remoteName + " in " + myGitRepository);
    }
    fetchSupport(myProject).fetch(myGitRepository, remote).showNotificationIfFailed();
  }

  @NotNull
  private DiffInfo doLoadDiffInfo(@NotNull final BranchInfo branch) throws VcsException {
    // TODO: make cancelable and abort old speculative requests (when intellij.vcs.git will allow to do so)
    String targetBranch = branch.getForkInfo().getRemoteName() + "/" + branch.getRemoteName();

    List<GitCommit> commits1 = GitHistoryUtils.history(myProject, myGitRepository.getRoot(), ".." + targetBranch);
    List<GitCommit> commits2 = GitHistoryUtils.history(myProject, myGitRepository.getRoot(), targetBranch + "..");
    Collection<Change> diff = GitChangeUtils.getDiff(myProject, myGitRepository.getRoot(), targetBranch, myCurrentBranch, null);
    CommitCompareInfo info = new CommitCompareInfo(CommitCompareInfo.InfoType.BRANCH_TO_HEAD);
    info.putTotalDiff(myGitRepository, diff);
    info.put(myGitRepository, commits1, commits2);

    return new DiffInfo(info, myCurrentBranch, targetBranch);
  }

  @Nullable
  private GithubPullRequestDetailed doCreatePullRequest(@NotNull ProgressIndicator indicator,
                                                        @NotNull final BranchInfo branch,
                                                        @NotNull final String title,
                                                        @NotNull final String description) {
    final GHRepositoryPath forkPath = branch.getForkInfo().getPath();

    final String head = myPath.getOwner() + ":" + myCurrentBranch;
    final String base = branch.getRemoteName();

    try {
      return myExecutor.execute(indicator,
                                GithubApiRequests.Repos.PullRequests
                                  .create(myServer, forkPath.getOwner(), forkPath.getRepository(), title, description, head, base));
    }
    catch (IOException e) {
      GithubNotifications.showError(myProject, GithubBundle.message("pull.request.cannot.create"), e);
      return null;
    }
  }

  public void configureRemote(@NotNull final ForkInfo fork) {
    myProgressManager.runProcessWithProgressSynchronously(() -> doConfigureRemote(fork),
                                                          GithubBundle.message("pull.request.create.remote.process.title"), false,
                                                          myProject);
  }

  @NotNull
  public Couple<String> getDefaultDescriptionMessage(@NotNull final BranchInfo branch) {
    Couple<String> message = branch.getDefaultMessage();
    if (message != null) return message;

    if (branch.getForkInfo().getRemoteName() == null) {
      return getSimpleDefaultDescriptionMessage(branch);
    }

    return myProgressManager.runProcessWithProgressSynchronously(() -> {
      String targetBranch = branch.getForkInfo().getRemoteName() + "/" + branch.getRemoteName();
      try {
        List<? extends VcsCommitMetadata> commits = GitHistoryUtils.collectCommitsMetadata(myProject, myGitRepository.getRoot(),
                                                                                           myCurrentBranch, targetBranch);
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
        GithubNotifications.showWarning(myProject, GithubBundle.message("cannot.collect.additional.data"), e);
        return getSimpleDefaultDescriptionMessage(branch);
      }
    }, GithubBundle.message("pull.request.create.collect.commits.process.title"), true, myProject);
  }

  @NotNull
  public Couple<String> getSimpleDefaultDescriptionMessage(@NotNull final BranchInfo branch) {
    Couple<String> message = Couple.of(myCurrentBranch, "");
    branch.setDefaultMessage(message);
    return message;
  }

  public boolean checkAction(@Nullable final BranchInfo branch) {
    if (branch == null) {
      GithubNotifications.showWarningDialog(myProject, GithubBundle.message("pull.request.cannot.create"), GithubBundle.message(
        "pull.request.validation.target.branch.not.selected"));
      return false;
    }

    DiffInfo info;
    try {
      info = myProgressManager.runProcessWithProgressSynchronously(
        () -> GithubUtil.runInterruptable(myProgressManager.getProgressIndicator(), () -> getDiffInfo(branch)),
        GithubBundle.message("pull.request.create.collect.diff.data.process.title"), false, myProject);
    }
    catch (IOException e) {
      GithubNotifications.showError(myProject, GithubBundle.message("cannot.collect.diff.data"), e);
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
        .showYesNoDialog(myProject, GithubBundle.message("pull.request.create.empty"),
                         GithubBundle.message("pull.request.create.branch.fully.merged", localBranchName, targetBranchName));
    }
    if (!info.getInfo().getHeadToBranchCommits(myGitRepository).isEmpty()) {
      return GithubNotifications
        .showYesNoDialog(myProject, GithubBundle.message("pull.request.not.fully.merged.dialog"),
                         GithubBundle.message("pull.request.not.fully.merged.dialog.message", targetBranchName, localBranchName));
    }

    return true;
  }

  public void createPullRequest(@NotNull final BranchInfo branch,
                                @NotNull final String title,
                                @NotNull final String description) {
    new Task.Backgroundable(myProject, GithubBundle.message("pull.request.create.process.title")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        LOG.info("Pushing current branch");
        indicator.setText(GithubBundle.message("pull.request.push.branch.process.title"));
        GitCommandResult result = myGit.push(myGitRepository, myRemoteName, myRemoteUrl, myCurrentBranch, true);
        if (!result.success()) {
          GithubNotifications.showError(GithubCreatePullRequestWorker.this.myProject, GithubBundle.message("pull.request.cannot.create"),
                                        GithubBundle.message("pull.request.push.failed", result.getErrorOutputAsHtmlString()));
          return;
        }

        LOG.info("Creating pull request");
        indicator.setText(GithubBundle.message("pull.request.create.process.title"));
        GithubPullRequestDetailed request = doCreatePullRequest(indicator, branch, title, description);
        if (request == null) {
          return;
        }

        GithubNotifications
          .showInfoURL(GithubCreatePullRequestWorker.this.myProject, GithubBundle.message("pull.request.successfully.created"),
                       GithubBundle.message("pull.request.num", request.getNumber()), request.getHtmlUrl());
      }
    }.queue();
  }

  @Nullable
  private List<GHRepositoryPath> getAvailableForks(@NotNull ProgressIndicator indicator) {
    try {
      List<GithubRepo> forks = GithubApiPagesLoader
        .loadAll(myExecutor, indicator, GithubApiRequests.Repos.Forks.pages(myServer, mySource.getOwner(), mySource.getRepository()));
      List<GHRepositoryPath> forkPaths = ContainerUtil.map(forks, GithubRepo::getFullPath);
      if (!forkPaths.contains(mySource)) return ContainerUtil.append(forkPaths, mySource);
      return forkPaths;
    }
    catch (IOException e) {
      GithubNotifications.showWarning(myProject, GithubBundle.message("pull.request.cannot.load.forks"), e);
      return null;
    }
  }

  public void showDiffDialog(@Nullable final BranchInfo branch) {
    if (branch == null) {
      GithubNotifications.showWarningDialog(myProject, GithubBundle.message("pull.request.cannot.show.diff"), "");
      return;
    }

    DiffInfo info;
    try {
      info = myProgressManager.runProcessWithProgressSynchronously(
        () -> GithubUtil.runInterruptable(myProgressManager.getProgressIndicator(), () -> getDiffInfo(branch)),
        GithubBundle.message("pull.request.create.collect.diff.data.process.title"), true, myProject);
    }
    catch (IOException e) {
      GithubNotifications.showError(myProject, GithubBundle.message("cannot.collect.diff.data"), e);
      return;
    }
    if (info == null) {
      GithubNotifications.showErrorDialog(myProject, GithubBundle.message("pull.request.cannot.show.diff"),
                                          GithubBundle.message("cannot.collect.diff.data"));
      return;
    }

    CompareBranchesDialog dialog =
      new CompareBranchesDialog(new GitCompareBranchesHelper(myProject), info.getTo(), info.getFrom(), info.getInfo(), myGitRepository,
                                true);
    dialog.show();
  }

  @Nullable
  public ForkInfo showTargetDialog() {
    if (myAvailableForks == null) {
      try {
        myAvailableForks = myProgressManager.runProcessWithProgressSynchronously(
          () -> getAvailableForks(myProgressManager.getProgressIndicator()), myCurrentBranch, false, myProject);
      }
      catch (ProcessCanceledException ignore) {
      }
    }

    Convertor<String, ForkInfo> getForkPath = user ->
      myProgressManager.runProcessWithProgressSynchronously(() -> findRepositoryByUser(myProgressManager.getProgressIndicator(), user),
                                                            GithubBundle.message("accessing.github"), false, myProject);

    GithubSelectForkDialog dialog = new GithubSelectForkDialog(myProject, myAvailableForks, getForkPath);
    DialogManager.show(dialog);
    if (!dialog.isOK()) {
      return null;
    }
    return dialog.getPath();
  }

  @Nullable
  private ForkInfo findRepositoryByUser(@NotNull final ProgressIndicator indicator, @NotNull final String user) {
    for (ForkInfo fork : myForks) {
      if (StringUtil.equalsIgnoreCase(user, fork.getPath().getOwner())) {
        return fork;
      }
    }

    try {
      GithubRepo repo;
      GithubRepoDetailed target = myExecutor.execute(indicator, GithubApiRequests.Repos.get(myServer, user, mySource.getRepository()));

      if (target != null && target.getSource() != null && StringUtil.equals(target.getSource().getUserName(), mySource.getOwner())) {
        repo = target;
      }
      else {
        repo = GithubApiPagesLoader
          .find(myExecutor, indicator, GithubApiRequests.Repos.Forks.pages(myServer, mySource.getOwner(), mySource.getRepository()),
                (fork) -> StringUtil.equalsIgnoreCase(fork.getUserName(), user));
      }

      if (repo == null) return null;
      return doAddFork(repo, indicator);
    }
    catch (IOException e) {
      GithubNotifications.showError(myProject, GithubBundle.message("cannot.find.repository"), e);
      return null;
    }
  }

  @Nullable
  public static GithubCreatePullRequestWorker create(@NotNull final Project project,
                                                     @NotNull GitRepository gitRepository,
                                                     @NotNull GitRemote remote,
                                                     @NotNull String remoteUrl,
                                                     @NotNull GithubApiRequestExecutor executor,
                                                     @NotNull GithubServerPath server) {
    ProgressManager progressManager = ProgressManager.getInstance();
    return progressManager.runProcessWithProgressSynchronously(() -> {
      Git git = ServiceManager.getService(Git.class);

      GHRepositoryPath path = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remoteUrl);
      if (path == null) {
        GithubNotifications
          .showError(project, GithubBundle.message("pull.request.cannot.create"), GithubBundle.message("cannot.process.remote", remoteUrl));
        return null;
      }

      GitLocalBranch currentBranch = gitRepository.getCurrentBranch();
      if (currentBranch == null) {
        GithubNotifications.showError(project, GithubBundle.message("pull.request.cannot.create"), GithubBundle.message(
          "pull.request.create.error.no.current.branch"));
        return null;
      }

      GithubCreatePullRequestWorker worker =
        new GithubCreatePullRequestWorker(project, git, gitRepository, executor, server,
                                          GithubGitHelper.getInstance(), progressManager, path, remote.getName(), remoteUrl,
                                          currentBranch.getName());

      try {
        worker.initForks(progressManager.getProgressIndicator());
      }
      catch (IOException e) {
        GithubNotifications.showError(project, GithubBundle.message("pull.request.cannot.create"), e);
        return null;
      }

      return worker;
    }, GithubBundle.message("pull.request.loading.data"), true, project);
  }

  public static class ForkInfo {
    @NotNull public final Object LOCK = new Object();

    // initial loading
    @NotNull private final GHRepositoryPath myPath;

    @NotNull private final String myDefaultBranch;
    @NotNull private final List<BranchInfo> myBranches;

    @Nullable private String myRemoteName;
    private boolean myProposedToCreateRemote;

    @Nullable private MasterFutureTask<Void> myFetchTask;

    public ForkInfo(@NotNull GHRepositoryPath path, @NotNull List<String> branches, @Nullable String defaultBranch) {
      myPath = path;
      myDefaultBranch = defaultBranch == null ? "master" : defaultBranch;
      myBranches = new ArrayList<>();
      for (String branchName : branches) {
        myBranches.add(new BranchInfo(branchName, this));
      }
    }

    @NotNull
    public GHRepositoryPath getPath() {
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
      return myPath.getOwner() + ":" + myPath.getRepository();
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

  public static final class DiffInfo {
    @NotNull private final CommitCompareInfo myInfo;
    @NotNull private final String myFrom;
    @NotNull private final String myTo;

    private DiffInfo(@NotNull CommitCompareInfo info, @NotNull String from, @NotNull String to) {
      myInfo = info;
      myFrom = from; // HEAD
      myTo = to;     // BASE
    }

    @NotNull
    public CommitCompareInfo getInfo() {
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

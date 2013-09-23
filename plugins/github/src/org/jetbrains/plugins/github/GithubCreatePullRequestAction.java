/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.containers.*;
import git4idea.DialogManager;
import git4idea.GitCommit;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
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
import icons.GithubIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.*;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationCanceledException;
import org.jetbrains.plugins.github.ui.GithubCreatePullRequestDialog;
import org.jetbrains.plugins.github.ui.GithubSelectForkDialog;
import org.jetbrains.plugins.github.util.*;

import java.io.IOException;
import java.util.*;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.jetbrains.plugins.github.util.GithubUtil.setVisibleEnabled;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestAction extends DumbAwareAction {
  private static final Logger LOG = GithubUtil.LOG;
  private static final String CANNOT_CREATE_PULL_REQUEST = "Can't create pull request";

  public GithubCreatePullRequestAction() {
    super("Create Pull Request", "Create pull request from current branch", GithubIcons.Github_icon);
  }

  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (project == null || project.isDefault()) {
      setVisibleEnabled(e, false, false);
      return;
    }

    final GitRepository gitRepository = GithubUtil.getGitRepository(project, file);
    if (gitRepository == null) {
      setVisibleEnabled(e, false, false);
      return;
    }

    if (!GithubUtil.isRepositoryOnGitHub(gitRepository)) {
      setVisibleEnabled(e, false, false);
      return;
    }

    setVisibleEnabled(e, true, true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);

    if (project == null || project.isDisposed() || !GithubUtil.testGitExecutable(project)) {
      return;
    }

    createPullRequest(project, file);
  }

  static void createPullRequest(@NotNull final Project project, @Nullable final VirtualFile file) {
    final Git git = ServiceManager.getService(Git.class);
    final GithubProjectSettings projectSettings = GithubProjectSettings.getInstance(project);

    final GitRepository gitRepository = GithubUtil.getGitRepository(project, file);
    if (gitRepository == null) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Can't find git repository");
      return;
    }
    gitRepository.update();

    Pair<GitRemote, String> remote = GithubUtil.findGithubRemote(gitRepository);
    if (remote == null) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Can't find GitHub remote");
      return;
    }
    final String remoteName = remote.getFirst().getName();
    final String remoteUrl = remote.getSecond();
    final GithubFullPath path = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remoteUrl);
    if (path == null) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Can't process remote: " + remoteUrl);
      return;
    }

    final GitLocalBranch currentBranch = gitRepository.getCurrentBranch();
    if (currentBranch == null) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "No current branch");
      return;
    }

    GithubFullPath forkPath = projectSettings.getCreatePullRequestDefaultRepo();
    if (forkPath == null) {
      openSelectTargetForkDialog(project, git, gitRepository, path, remoteName, remoteUrl, currentBranch.getName());
      return;
    }

    performCreatePullRequest(project, git, gitRepository, path, forkPath, remoteName, remoteUrl, currentBranch.getName());
  }

  private static void performCreatePullRequest(@NotNull final Project project,
                                               @NotNull final Git git,
                                               @NotNull final GitRepository gitRepository,
                                               @NotNull final GithubFullPath path,
                                               @NotNull final GithubFullPath forkPath,
                                               @NotNull final String remoteName,
                                               @NotNull final String remoteUrl,
                                               @NotNull final String currentBranch) {
    final GithubInfo info = prepareInfoWithModal(project, forkPath, gitRepository);
    if (info == null) {
      return;
    }
    final GithubAuthData auth = info.getAuthData();

    Consumer<String> showDiff = info.getTargetRemote() != null ? new Consumer<String>() {
      @Override
      public void consume(String branch) {
        showDiffByRef(project, branch, gitRepository, info.getTargetRemote(), currentBranch);
      }
    } : null;
    Runnable showSelectForkDialog = new Runnable() {
      @Override
      public void run() {
        openSelectTargetForkDialog(project, git, gitRepository, path, remoteName, remoteUrl, currentBranch);
      }
    };
    final GithubCreatePullRequestDialog dialog =
      new GithubCreatePullRequestDialog(project, forkPath.getFullName(), info.getBranches(), showDiff, showSelectForkDialog);
    DialogManager.show(dialog);
    if (!dialog.isOK()) {
      return;
    }

    new Task.Backgroundable(project, "Creating pull request...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        LOG.info("Pushing current branch");
        indicator.setText("Pushing current branch...");
        GitCommandResult result = git.push(gitRepository, remoteName, remoteUrl, currentBranch, true);
        if (!result.success()) {
          GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Push failed:<br/>" + result.getErrorOutputAsHtmlString());
          return;
        }

        String from = path.getUser() + ":" + currentBranch;
        String onto = dialog.getTargetBranch();

        LOG.info("Creating pull request");
        indicator.setText("Creating pull request...");
        GithubPullRequest request =
          createPullRequest(project, auth, forkPath, dialog.getRequestTitle(), dialog.getDescription(), from, onto);
        if (request == null) {
          return;
        }

        GithubNotifications
          .showInfoURL(project, "Successfully created pull request", "Pull Request #" + request.getNumber(), request.getHtmlUrl());
      }
    }.queue();
  }

  @Nullable
  private static GithubInfo prepareInfoWithModal(@NotNull final Project project,
                                                 @NotNull final GithubFullPath forkPath,
                                                 @NotNull final GitRepository gitRepository) {
    try {
      return GithubUtil
        .computeValueInModal(project, "Access to GitHub", new ThrowableConvertor<ProgressIndicator, GithubInfo, IOException>() {
          @Override
          public GithubInfo convert(ProgressIndicator indicator) throws IOException {
            // configure remote
            GitRemote targetRemote = GithubUtil.findGithubRemote(gitRepository, forkPath);
            String targetRemoteName = targetRemote == null ? null : targetRemote.getName();
            if (targetRemoteName == null) {
              final AtomicReference<Integer> responseRef = new AtomicReference<Integer>();
              ApplicationManager.getApplication().invokeAndWait(new Runnable() {
                @Override
                public void run() {
                  responseRef.set(GithubNotifications
                                    .showYesNoDialog(project, "Can't find remote", "Configure remote for '" + forkPath.getUser() + "'?"));
                }
              }, indicator.getModalityState());
              if (responseRef.get() == Messages.YES) {
                targetRemoteName = configureRemote(project, gitRepository, forkPath);
              }
            }

            // load available branches
            final AtomicReference<List<String>> reposRef = new AtomicReference<List<String>>();
            final GithubAuthData auth =
              GithubUtil.runAndGetValidAuth(project, indicator, new ThrowableConsumer<GithubAuthData, IOException>() {
                @Override
                public void consume(GithubAuthData authData) throws IOException {
                  reposRef.set(ContainerUtil.map(GithubApiUtil.getRepoBranches(authData, forkPath.getUser(), forkPath.getRepository()),
                                                 new Function<GithubBranch, String>() {
                                                   @Override
                                                   public String fun(GithubBranch githubBranch) {
                                                     return githubBranch.getName();
                                                   }
                                                 }));
                }
              });


            // fetch
            if (targetRemoteName != null) {
              GitFetchResult result = new GitFetcher(project, indicator, false).fetch(gitRepository.getRoot(), targetRemoteName, null);
              if (!result.isSuccess()) {
                GitFetcher.displayFetchResult(project, result, null, result.getErrors());
                targetRemoteName = null;
              }
            }

            return new GithubInfo(auth, reposRef.get(), targetRemoteName);
          }
        });
    }
    catch (GithubAuthenticationCanceledException e) {
      return null;
    }
    catch (IOException e) {
      GithubNotifications.showErrorDialog(project, CANNOT_CREATE_PULL_REQUEST, e);
      return null;
    }
  }

  @Nullable
  private static String configureRemote(@NotNull Project project, @NotNull GitRepository gitRepository, @NotNull GithubFullPath forkPath) {
    String url = GithubUrlUtil.getCloneUrl(forkPath);

    if (GithubUtil.addGithubRemote(project, gitRepository, forkPath.getUser(), url)) {
      return forkPath.getUser();
    }
    else {
      return null;
    }
  }

  @Nullable
  private static GithubPullRequest createPullRequest(@NotNull Project project,
                                                     @NotNull GithubAuthData auth,
                                                     @NotNull GithubFullPath targetRepo,
                                                     @NotNull String title,
                                                     @NotNull String description,
                                                     @NotNull String from,
                                                     @NotNull String onto) {
    try {
      return GithubApiUtil.createPullRequest(auth, targetRepo.getUser(), targetRepo.getRepository(), title, description, from, onto);
    }
    catch (IOException e) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, e);
      return null;
    }
  }

  private static void showDiffByRef(@NotNull final Project project,
                                    @Nullable final String branch,
                                    @NotNull final GitRepository gitRepository,
                                    @NotNull final String targetRemote,
                                    @NotNull final String currentBranch) {
    if (branch == null) {
      return;
    }

    DiffInfo info = GithubUtil.computeValueInModal(project, "Collecting diff data...", new Convertor<ProgressIndicator, DiffInfo>() {
      @Override
      @Nullable
      public DiffInfo convert(ProgressIndicator indicator) {
        return getDiffInfo(project, gitRepository, currentBranch, targetRemote + "/" + branch);
      }
    });
    if (info == null) {
      GithubNotifications.showErrorDialog(project, "Can't show diff", "Can't get diff info");
      return;
    }

    GitCompareBranchesDialog dialog = new GitCompareBranchesDialog(project, info.getTo(), info.getFrom(), info.getInfo(), gitRepository);
    dialog.show();
  }

  @Nullable
  private static DiffInfo getDiffInfo(@NotNull final Project project,
                                      @NotNull final GitRepository repository,
                                      @NotNull final String currentBranch,
                                      @NotNull final String targetBranch) {
    try {
      List<GitCommit> commits = GitHistoryUtils.history(project, repository.getRoot(), targetBranch + "..");
      Collection<Change> diff = GitChangeUtils.getDiff(repository.getProject(), repository.getRoot(), targetBranch, currentBranch, null);
      GitCommitCompareInfo info = new GitCommitCompareInfo(GitCommitCompareInfo.InfoType.BRANCH_TO_HEAD);
      info.put(repository, diff);
      info.put(repository, Pair.<List<GitCommit>, List<GitCommit>>create(new ArrayList<GitCommit>(), commits));
      return new DiffInfo(info, currentBranch, targetBranch);
    }
    catch (VcsException e) {
      LOG.info(e);
      return null;
    }
  }

  private static void openSelectTargetForkDialog(@NotNull final Project project,
                                                 @NotNull final Git git,
                                                 @NotNull final GitRepository gitRepository,
                                                 @NotNull final GithubFullPath path,
                                                 @NotNull final String remoteName,
                                                 @NotNull final String remoteUrl,
                                                 @NotNull final String currentBranch) {
    final GithubInfo2 info = getAvailableForksInModal(project, gitRepository, path);
    if (info == null) {
      return;
    }

    Convertor<String, GithubFullPath> getForkPath = new Convertor<String, GithubFullPath>() {
      @Nullable
      @Override
      public GithubFullPath convert(final String user) {
        return GithubUtil.computeValueInModal(project, "Access to GitHub", new Convertor<ProgressIndicator, GithubFullPath>() {
          @Nullable
          @Override
          public GithubFullPath convert(ProgressIndicator o) {
            return findRepositoryByUser(project, user, info.getForks(), info.getAuthData(), info.getSource());
          }
        });
      }
    };
    GithubSelectForkDialog dialog = new GithubSelectForkDialog(project, info.getForks(), getForkPath);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    performCreatePullRequest(project, git, gitRepository, path, dialog.getPath(), remoteName, remoteUrl, currentBranch);
  }

  @Nullable
  private static GithubInfo2 getAvailableForksInModal(@NotNull final Project project,
                                                      @NotNull final GitRepository gitRepository,
                                                      @NotNull final GithubFullPath path) {
    return GithubUtil.computeValueInModal(project, "Access to GitHub", new Convertor<ProgressIndicator, GithubInfo2>() {
      @Nullable
      @Override
      public GithubInfo2 convert(ProgressIndicator indicator) {
        try {
          final Set<GithubFullPath> forks = new HashSet<GithubFullPath>();

          // GitHub
          final AtomicReference<GithubRepo> sourceRef = new AtomicReference<GithubRepo>();
          GithubAuthData authData = GithubUtil.runAndGetValidAuth(project, indicator, new ThrowableConsumer<GithubAuthData, IOException>() {
            @Override
            public void consume(GithubAuthData authData) throws IOException {
              GithubRepoDetailed repo = GithubApiUtil.getDetailedRepoInfo(authData, path.getUser(), path.getRepository());
              forks.add(path);
              if (repo.getParent() != null) {
                forks.add(repo.getParent().getFullPath());
              }
              if (repo.getSource() != null) {
                forks.add(repo.getSource().getFullPath());
              }
              if (repo.getSource() != null) {
                sourceRef.set(repo.getSource());
              }
              else {
                sourceRef.set(repo);
              }
            }
          });

          // Git
          forks.addAll(getAvailableForksFromGit(gitRepository));

          return new GithubInfo2(forks, authData, sourceRef.get());
        }
        catch (GithubAuthenticationCanceledException e) {
          return null;
        }
        catch (IOException e) {
          GithubNotifications.showErrorDialog(project, CANNOT_CREATE_PULL_REQUEST, e);
          return null;
        }
      }
    });
  }

  @NotNull
  private static List<GithubFullPath> getAvailableForksFromGit(@NotNull GitRepository gitRepository) {
    List<GithubFullPath> forks = new ArrayList<GithubFullPath>();
    for (GitRemoteBranch remoteBranch : gitRepository.getBranches().getRemoteBranches()) {
      for (String url : remoteBranch.getRemote().getUrls()) {
        if (GithubUrlUtil.isGithubUrl(url)) {
          GithubFullPath path = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url);
          if (path != null) {
            forks.add(path);
            break;
          }
        }
      }
    }
    return forks;
  }

  @Nullable
  private static GithubFullPath findRepositoryByUser(@NotNull Project project,
                                                     @NotNull String user,
                                                     @NotNull Set<GithubFullPath> forks,
                                                     @NotNull GithubAuthData auth,
                                                     @NotNull GithubRepo source) {
    for (GithubFullPath path : forks) {
      if (StringUtil.equalsIgnoreCase(user, path.getUser())) {
        return path;
      }
    }

    try {
      GithubRepoDetailed target = GithubApiUtil.getDetailedRepoInfo(auth, user, source.getName());
      if (target.getSource() != null && StringUtil.equals(target.getSource().getUserName(), source.getUserName())) {
        return target.getFullPath();
      }
    }
    catch (IOException ignore) {
      // such repo may not exist
    }

    try {
      GithubRepo fork = GithubApiUtil.findForkByUser(auth, source.getUserName(), source.getName(), user);
      if (fork != null) {
        return fork.getFullPath();
      }
    }
    catch (IOException e) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, e);
    }

    return null;
  }

  private static class GithubInfo {
    @NotNull private final List<String> myBranches;
    @NotNull private final GithubAuthData myAuthData;
    @Nullable private final String myTargetRemote;

    private GithubInfo(@NotNull GithubAuthData authData, @NotNull List<String> repo, @Nullable String targetRemote) {
      myAuthData = authData;
      myBranches = repo;
      myTargetRemote = targetRemote;
    }

    @NotNull
    public List<String> getBranches() {
      return myBranches;
    }

    @NotNull
    public GithubAuthData getAuthData() {
      return myAuthData;
    }

    @Nullable
    public String getTargetRemote() {
      return myTargetRemote;
    }
  }

  private static class GithubInfo2 {
    @NotNull private final Set<GithubFullPath> myForks;
    @NotNull private final GithubAuthData myAuthData;
    @NotNull private final GithubRepo mySource;

    private GithubInfo2(@NotNull Set<GithubFullPath> forks, @NotNull GithubAuthData authData, @NotNull GithubRepo source) {
      myForks = forks;
      myAuthData = authData;
      mySource = source;
    }

    @NotNull
    public Set<GithubFullPath> getForks() {
      return myForks;
    }

    @NotNull
    public GithubAuthData getAuthData() {
      return myAuthData;
    }

    @NotNull
    public GithubRepo getSource() {
      return mySource;
    }
  }

  private static class DiffInfo {
    @NotNull private final GitCommitCompareInfo myInfo;
    @NotNull private final String myFrom;
    @NotNull private final String myTo;

    private DiffInfo(@NotNull GitCommitCompareInfo info, @NotNull String from, @NotNull String to) {
      myInfo = info;
      myFrom = from;
      myTo = to;
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
}

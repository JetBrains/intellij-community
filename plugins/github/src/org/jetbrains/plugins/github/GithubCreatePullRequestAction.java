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
import com.intellij.openapi.actionSystem.CommonDataKeys;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import git4idea.DialogManager;
import git4idea.GitCommit;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.branch.GitBranchUtil;
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
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.io.IOException;
import java.util.*;
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
    final Project project = e.getData(CommonDataKeys.PROJECT);
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
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);

    if (project == null || project.isDisposed() || !GithubUtil.testGitExecutable(project)) {
      return;
    }

    createPullRequest(project, file);
  }

  static void createPullRequest(@NotNull final Project project, @Nullable final VirtualFile file) {
    final Git git = ServiceManager.getService(Git.class);

    final GitRepository repository = GithubUtil.getGitRepository(project, file);
    if (repository == null) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Can't find git repository");
      return;
    }
    repository.update();

    Pair<GitRemote, String> remote = GithubUtil.findGithubRemote(repository);
    if (remote == null) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Can't find GitHub remote");
      return;
    }
    final String remoteUrl = remote.getSecond();
    final String remoteName = remote.getFirst().getName();

    GithubFullPath userAndRepo = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remoteUrl);
    if (userAndRepo == null) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Can't process remote: " + remoteUrl);
      return;
    }

    final GitLocalBranch currentBranch = repository.getCurrentBranch();
    if (currentBranch == null) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "No current branch");
      return;
    }

    String upstreamUrl = GithubUtil.findUpstreamRemote(repository);
    GithubFullPath upstreamUserAndRepo =
      upstreamUrl == null || !GithubUrlUtil.isGithubUrl(upstreamUrl) ? null : GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(upstreamUrl);

    final Map<String, String> forks = new HashMap<String, String>();
    final Set<RemoteBranch> branches = new HashSet<RemoteBranch>();
    addAvailableBranchesFromGit(repository, forks, branches);
    GithubInfo info = loadGithubInfoAndBranchesWithModal(project, userAndRepo, upstreamUserAndRepo, forks, branches);
    if (info == null) {
      return;
    }
    final GithubRepoDetailed repo = info.getRepo();
    final GithubAuthData auth = info.getAuthData();

    GithubRepo parent = repo.getParent();
    String defaultBranch =
      parent == null || parent.getDefaultBranch() == null ? null : parent.getUserName() + ":" + parent.getDefaultBranch();
    Collection<String> suggestions = ContainerUtil.map(branches, new Function<RemoteBranch, String>() {
      @Override
      public String fun(RemoteBranch remoteBranch) {
        return remoteBranch.getReference();
      }
    });
    Consumer<String> showDiff = new Consumer<String>() {
      @Override
      public void consume(String ref) {
        showDiffByRef(project, ref, repository, currentBranch.getName(), auth, forks, branches, repo.getSource());
      }
    };
    final GithubCreatePullRequestDialog dialog = new GithubCreatePullRequestDialog(project, suggestions, defaultBranch, showDiff);
    DialogManager.show(dialog);
    if (!dialog.isOK()) {
      return;
    }

    new Task.Backgroundable(project, "Creating pull request...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        LOG.info("Pushing current branch");
        indicator.setText("Pushing current branch...");
        GitCommandResult result = git.push(repository, remoteName, remoteUrl, currentBranch.getName(), true);
        if (!result.success()) {
          GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Push failed:<br/>" + result.getErrorOutputAsHtmlString());
          return;
        }

        String from = repo.getUserName() + ":" + currentBranch.getName();
        String onto = dialog.getTargetBranch();
        String targetUser = onto.substring(0, onto.indexOf(':'));

        GithubFullPath targetRepo = findRepositoryByUser(project, targetUser, forks, auth, repo.getSource());
        if (targetRepo == null) {
          GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Can't find repository for specified branch: " + onto);
          return;
        }

        LOG.info("Creating pull request");
        indicator.setText("Creating pull request...");
        GithubPullRequest request =
          createPullRequest(project, auth, targetRepo, dialog.getRequestTitle(), dialog.getDescription(), from, onto);
        if (request == null) {
          return;
        }

        GithubNotifications
          .showInfoURL(project, "Successfully created pull request", "Pull Request #" + request.getNumber(), request.getHtmlUrl());
      }
    }.queue();
  }

  @Nullable
  private static GithubInfo loadGithubInfoAndBranchesWithModal(@NotNull final Project project,
                                                               @NotNull final GithubFullPath userAndRepo,
                                                               @Nullable final GithubFullPath upstreamUserAndRepo,
                                                               @NotNull final Map<String, String> forks,
                                                               @NotNull final Set<RemoteBranch> branches) {
    try {
      return GithubUtil
        .computeValueInModal(project, "Access to GitHub", new ThrowableConvertor<ProgressIndicator, GithubInfo, IOException>() {
          @Override
          public GithubInfo convert(ProgressIndicator indicator) throws IOException {
            final AtomicReference<GithubRepoDetailed> reposRef = new AtomicReference<GithubRepoDetailed>();
            final GithubAuthData auth =
              GithubUtil.runAndGetValidAuth(project, indicator, new ThrowableConsumer<GithubAuthData, IOException>() {
                @Override
                public void consume(GithubAuthData authData) throws IOException {
                  reposRef.set(GithubApiUtil.getDetailedRepoInfo(authData, userAndRepo.getUser(), userAndRepo.getRepository()));
                }
              });
            addAvailableBranchesFromGithub(project, auth, reposRef.get(), upstreamUserAndRepo, forks, branches);
            return new GithubInfo(auth, reposRef.get());
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
  private static GithubFullPath findRepositoryByUser(@NotNull Project project,
                                                     @NotNull String user,
                                                     @NotNull Map<String, String> forks,
                                                     @NotNull GithubAuthData auth,
                                                     @Nullable GithubRepo source) {
    for (Map.Entry<String, String> entry : forks.entrySet()) {
      if (StringUtil.equalsIgnoreCase(user, entry.getKey())) {
        return new GithubFullPath(entry.getKey(), entry.getValue());
      }
    }

    if (source != null) {
      try {
        GithubRepoDetailed target = GithubApiUtil.getDetailedRepoInfo(auth, user, source.getName());
        if (target.getSource() != null && StringUtil.equals(target.getSource().getUserName(), source.getUserName())) {
          forks.put(target.getUserName(), target.getName());
          return target.getFullPath();
        }
      }
      catch (IOException ignore) {
        // such repo may not exist
      }

      try {
        GithubRepo fork = GithubApiUtil.findForkByUser(auth, source.getUserName(), source.getName(), user);
        if (fork != null) {
          forks.put(fork.getUserName(), fork.getName());
          return fork.getFullPath();
        }
      }
      catch (IOException e) {
        GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, e);
      }
    }

    return null;
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

  private static void addAvailableBranchesFromGit(@NotNull GitRepository gitRepository,
                                                  @NotNull Map<String, String> forks,
                                                  @NotNull Set<RemoteBranch> branches) {
    for (GitRemoteBranch remoteBranch : gitRepository.getBranches().getRemoteBranches()) {
      for (String url : remoteBranch.getRemote().getUrls()) {
        if (GithubUrlUtil.isGithubUrl(url)) {
          GithubFullPath path = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url);
          if (path != null) {
            forks.put(path.getUser(), path.getRepository());
            branches.add(new RemoteBranch(path.getUser(), remoteBranch.getNameForRemoteOperations(), remoteBranch));
            break;
          }
        }
      }
    }
  }

  private static void addAvailableBranchesFromGithub(@NotNull final Project project,
                                                     @NotNull final GithubAuthData auth,
                                                     @NotNull final GithubRepoDetailed repo,
                                                     @Nullable final GithubFullPath upstreamPath,
                                                     @NotNull Map<String, String> forks,
                                                     @NotNull Set<RemoteBranch> branches) {
    try {
      final GithubRepo parent = repo.getParent();
      final GithubRepo source = repo.getSource();

      forks.put(repo.getUserName(), repo.getName());
      branches.addAll(getBranches(auth, repo.getUserName(), repo.getName()));

      if (parent != null) {
        forks.put(parent.getUserName(), parent.getName());
        branches.addAll(getBranches(auth, parent.getUserName(), parent.getName()));
      }

      if (source != null && !equals(source, parent)) {
        forks.put(source.getUserName(), source.getName());
        branches.addAll(getBranches(auth, source.getUserName(), source.getName()));
      }

      if (upstreamPath != null && !equals(upstreamPath, repo) && !equals(upstreamPath, parent) && !equals(upstreamPath, source)) {
        forks.put(upstreamPath.getUser(), upstreamPath.getRepository());
        branches.addAll(getBranches(auth, upstreamPath.getUser(), upstreamPath.getRepository()));
      }
    }
    catch (IOException e) {
      GithubNotifications.showError(project, "Can't load available branches", e);
    }
  }

  @NotNull
  private static List<RemoteBranch> getBranches(@NotNull GithubAuthData auth, @NotNull final String user, @NotNull final String repo)
    throws IOException {
    List<GithubBranch> branches = GithubApiUtil.getRepoBranches(auth, user, repo);
    return ContainerUtil.map(branches, new Function<GithubBranch, RemoteBranch>() {
      @Override
      public RemoteBranch fun(GithubBranch branch) {
        return new RemoteBranch(user, branch.getName());
      }
    });
  }

  private static boolean equals(@NotNull GithubRepo repo1, @Nullable GithubRepo repo2) {
    if (repo2 == null) {
      return false;
    }
    return StringUtil.equals(repo1.getUserName(), repo2.getUserName());
  }

  private static boolean equals(@NotNull GithubFullPath repo1, @Nullable GithubRepo repo2) {
    if (repo2 == null) {
      return false;
    }
    return StringUtil.equals(repo1.getUser(), repo2.getUserName());
  }

  private static void showDiffByRef(@NotNull final Project project,
                                    @Nullable final String ref,
                                    @NotNull final GitRepository gitRepository,
                                    @NotNull final String currentBranch,
                                    @NotNull final GithubAuthData auth,
                                    @NotNull final Map<String, String> forks,
                                    @NotNull final Set<RemoteBranch> branches,
                                    @Nullable final GithubRepo source) {
    if (ref == null) {
      return;
    }

    DiffInfo info = GithubUtil.computeValueInModal(project, "Collecting diff data...", new Convertor<ProgressIndicator, DiffInfo>() {
      @Override
      @Nullable
      public DiffInfo convert(ProgressIndicator indicator) {
        List<String> list = StringUtil.split(ref, ":");
        assert list.size() == 2 : ref;
        final String user = list.get(0);
        final String branch = list.get(1);

        TargetBranchInfo targetBranchInfo;
        RemoteBranch remoteBranch = findRemoteBranch(branches, user, branch);
        if (remoteBranch != null && remoteBranch.getRemoteBranch() != null) {
          targetBranchInfo = getTargetBranchInfo(remoteBranch.getRemoteBranch());
        }
        else {
          GithubFullPath forkPath = findRepositoryByUser(project, user, forks, auth, source);
          if (forkPath == null) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                GithubNotifications.showErrorDialog(project, "Can't show diff", "Can't find fork for user '" + user + "'");
              }
            }, indicator.getModalityState());
            return null;
          }

          targetBranchInfo = findRemote(branch, gitRepository, forkPath);
          if (targetBranchInfo == null) {
            final AtomicReference<Integer> responseRef = new AtomicReference<Integer>();
            ApplicationManager.getApplication().invokeAndWait(new Runnable() {
              @Override
              public void run() {
                responseRef.set(GithubNotifications.showYesNoDialog(project, "Can't find remote", "Configure remote for '" + user + "'?"));
              }
            }, indicator.getModalityState());
            if (responseRef.get() != Messages.YES) {
              return null;
            }

            targetBranchInfo = configureRemote(project, user, branch, gitRepository, forkPath);
          }
        }
        if (targetBranchInfo == null) {
          return null;
        }

        GitFetchResult result = new GitFetcher(project, indicator, false)
          .fetch(gitRepository.getRoot(), targetBranchInfo.getRemote(), targetBranchInfo.getBranchNameForRemoteOperations());
        if (!result.isSuccess()) {
          GitFetcher.displayFetchResult(project, result, null, result.getErrors());
          return null;
        }

        DiffInfo info = getDiffInfo(project, gitRepository, currentBranch, targetBranchInfo.getBranchNameForLocalOperations());
        if (info == null) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              GithubNotifications.showErrorDialog(project, "Can't show diff", "Can't get diff info");
            }
          }, indicator.getModalityState());
          return null;
        }
        return info;
      }
    });
    if (info == null) {
      return;
    }

    GitCompareBranchesDialog dialog = new GitCompareBranchesDialog(project, info.getTo(), info.getFrom(), info.getInfo(), gitRepository);
    dialog.show();
  }

  private static TargetBranchInfo getTargetBranchInfo(@NotNull GitRemoteBranch remoteBranch) {
    return new TargetBranchInfo(remoteBranch.getRemote().getName(), remoteBranch.getNameForRemoteOperations());
  }

  @Nullable
  private static TargetBranchInfo findRemote(@NotNull String branch,
                                             @NotNull GitRepository gitRepository,
                                             @NotNull GithubFullPath forkPath) {
    GitRemote remote = GithubUtil.findGithubRemote(gitRepository, forkPath);
    return remote == null ? null : new TargetBranchInfo(remote.getName(), branch);
  }

  @Nullable
  private static TargetBranchInfo configureRemote(@NotNull Project project,
                                                  @NotNull String user,
                                                  @NotNull String branch,
                                                  @NotNull GitRepository gitRepository,
                                                  @NotNull GithubFullPath forkPath) {
    String url = GithubUrlUtil.getCloneUrl(forkPath);

    if (GithubUtil.addGithubRemote(project, gitRepository, user, url)) {
      return new TargetBranchInfo(user, branch);
    }
    else {
      return null;
    }
  }

  @Nullable
  private static RemoteBranch findRemoteBranch(@NotNull Set<RemoteBranch> branches, @NotNull String user, @NotNull String branch) {
    for (RemoteBranch remoteBranch : branches) {
      if (StringUtil.equalsIgnoreCase(user, remoteBranch.getUser()) && StringUtil.equals(branch, remoteBranch.getBranch())) {
        return remoteBranch;
      }
    }

    return null;
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

  private static class RemoteBranch {
    @NotNull final String myUser;
    @NotNull final String myBranch;

    @Nullable final GitRemoteBranch myRemoteBranch;

    private RemoteBranch(@NotNull String user, @NotNull String branch) {
      this(user, branch, null);
    }

    public RemoteBranch(@NotNull String user, @NotNull String branch, @Nullable GitRemoteBranch localBranch) {
      myUser = user;
      myBranch = branch;
      myRemoteBranch = localBranch;
    }

    @NotNull
    public String getReference() {
      return myUser + ":" + myBranch;
    }

    @NotNull
    public String getUser() {
      return myUser;
    }

    @NotNull
    public String getBranch() {
      return myBranch;
    }

    @Nullable
    public GitRemoteBranch getRemoteBranch() {
      return myRemoteBranch;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      RemoteBranch that = (RemoteBranch)o;

      if (!StringUtil.equals(myUser, that.myUser)) return false;
      if (!StringUtil.equals(myBranch, that.myBranch)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myUser.hashCode();
      result = 31 * result + myBranch.hashCode();
      return result;
    }
  }

  private static class GithubInfo {
    @NotNull private final GithubRepoDetailed myRepo;
    @NotNull private final GithubAuthData myAuthData;

    private GithubInfo(@NotNull GithubAuthData authData, @NotNull GithubRepoDetailed repo) {
      myAuthData = authData;
      myRepo = repo;
    }

    @NotNull
    public GithubRepoDetailed getRepo() {
      return myRepo;
    }

    @NotNull
    public GithubAuthData getAuthData() {
      return myAuthData;
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

  private static class TargetBranchInfo {
    @NotNull private final String myRemote;
    @NotNull private final String myName;
    @NotNull private final String myNameAtRemote;

    private TargetBranchInfo(@NotNull String remote, @NotNull String nameAtRemote) {
      myRemote = remote;
      myNameAtRemote = GitBranchUtil.stripRefsPrefix(nameAtRemote);
      myName = myRemote + "/" + myNameAtRemote;
    }

    @NotNull
    public String getRemote() {
      return myRemote;
    }

    @NotNull
    public String getBranchNameForLocalOperations() {
      return myName;
    }

    @NotNull
    public String getBranchNameForRemoteOperations() {
      return myNameAtRemote;
    }
  }
}

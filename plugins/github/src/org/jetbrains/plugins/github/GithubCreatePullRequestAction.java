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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;

import javax.swing.SwingWorker;

import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import git4idea.DialogManager;
import git4idea.GitLocalBranch;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import icons.GithubIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.*;
import org.jetbrains.plugins.github.ui.GithubCreatePullRequestDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.plugins.github.GithubUtil.setVisibleEnabled;

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

    if (StringUtil.isEmptyOrSpaces(GithubSettings.getInstance().getLogin())) {
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

    final GitRepository repository = GithubUtil.getGitRepository(project, file);
    if (repository == null) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Can't find git repository");
      return;
    }

    final Pair<GitRemote, String> remote = GithubUtil.findGithubRemote(repository);
    if (remote == null) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Can't find GitHub remote");
      return;
    }
    final String remoteUrl = remote.getSecond();
    final String remoteName = remote.getFirst().getName();
    String upstreamUrl = GithubUtil.findUpstreamRemote(repository);
    final GithubFullPath upstreamUserAndRepo =
      upstreamUrl == null || !GithubUrlUtil.isGithubUrl(upstreamUrl) ? null : GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(upstreamUrl);

    final GithubFullPath userAndRepo = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remoteUrl);
    if (userAndRepo == null) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Can't process remote: " + remoteUrl);
      return;
    }

    final GitLocalBranch currentBranch = repository.getCurrentBranch();
    if (currentBranch == null) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "No current branch");
      return;
    }

    final GithubInfo info = loadGithubInfoWithModal(project, userAndRepo);
    if (info == null) {
      return;
    }

    final GithubCreatePullRequestDialog dialog = new GithubCreatePullRequestDialog(project);
    initLoadAvailableBranches(project, info, upstreamUserAndRepo, dialog);
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

        LOG.info("Performing create request");
        indicator.setText("Performing create request...");
        GithubPullRequest request = createPullRequest(project, info, dialog, currentBranch.getName(), upstreamUserAndRepo);
        if (request == null) {
          return;
        }

        GithubNotifications
          .showInfoURL(project, "Successfully created pull request", "Pull Request #" + request.getNumber(), request.getHtmlUrl());
      }
    }.queue();
  }

  @Nullable
  private static GithubInfo loadGithubInfoWithModal(@NotNull final Project project, @NotNull final GithubFullPath userAndRepo) {
    final Ref<GithubInfo> githubInfoRef = new Ref<GithubInfo>();
    final Ref<IOException> exceptionRef = new Ref<IOException>();
    ProgressManager.getInstance().run(new Task.Modal(project, "Access to GitHub", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          final Ref<GithubRepoDetailed> reposRef = new Ref<GithubRepoDetailed>();
          final GithubAuthData auth =
            GithubUtil.runAndGetValidAuth(project, indicator, new ThrowableConsumer<GithubAuthData, IOException>() {
              @Override
              public void consume(GithubAuthData authData) throws IOException {
                reposRef.set(GithubApiUtil.getDetailedRepoInfo(authData, userAndRepo.getUser(), userAndRepo.getRepository()));
              }
            });
          githubInfoRef.set(new GithubInfo(auth, reposRef.get()));
        }
        catch (IOException e) {
          exceptionRef.set(e);
        }
      }
    });
    if (!exceptionRef.isNull()) {
      if (exceptionRef.get() instanceof GithubAuthenticationCanceledException) {
        return null;
      }
      GithubNotifications.showErrorDialog(project, CANNOT_CREATE_PULL_REQUEST, exceptionRef.get());
      return null;
    }
    return githubInfoRef.get();
  }

  @Nullable
  private static GithubPullRequest createPullRequest(@NotNull Project project,
                                                     @NotNull GithubInfo info,
                                                     @NotNull final GithubCreatePullRequestDialog dialog,
                                                     @NotNull final String headBranch,
                                                     @Nullable final GithubFullPath upstreamPath) {
    GithubAuthData auth = info.getAuthData();
    GithubRepoDetailed repo = info.getRepo();

    String from = repo.getUserName() + ":" + headBranch;
    String onto = dialog.getTargetBranch();

    GithubFullPath target = getTargetRepository(project, auth, onto, repo, upstreamPath);
    if (target == null) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Can't find repository for specified branch: " + onto);
      return null;
    }

    try {
      return GithubApiUtil
        .createPullRequest(auth, target.getUser(), target.getRepository(), dialog.getRequestTitle(), dialog.getDescription(), from, onto);
    }
    catch (IOException e) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, e);
      return null;
    }
  }

  @Nullable
  private static GithubFullPath getTargetRepository(@NotNull Project project,
                                                    @NotNull GithubAuthData auth,
                                                    @NotNull String onto,
                                                    @NotNull GithubRepoDetailed repo,
                                                    @Nullable GithubFullPath upstreamPath) {
    String targetUser = onto.substring(0, onto.indexOf(':'));
    @Nullable GithubRepo parent = repo.getParent();
    @Nullable GithubRepo source = repo.getSource();

    if (isRepoOwner(targetUser, repo)) {
      return repo.getFullPath();
    }
    if (parent != null && isRepoOwner(targetUser, parent)) {
      return parent.getFullPath();
    }
    if (source != null && isRepoOwner(targetUser, source)) {
      return source.getFullPath();
    }
    if (upstreamPath != null && StringUtil.equalsIgnoreCase(targetUser, upstreamPath.getUser())) {
      return upstreamPath;
    }
    if (source != null) {
      try {
        GithubRepoDetailed target = GithubApiUtil.getDetailedRepoInfo(auth, targetUser, repo.getName());
        if (target.getSource() != null && StringUtil.equalsIgnoreCase(target.getSource().getUserName(), source.getUserName())) {
          return target.getFullPath();
        }
      }
      catch (IOException ignore) {
      }

      try {
        GithubRepo fork = GithubApiUtil.findForkByUser(auth, source.getUserName(), source.getName(), targetUser);
        if (fork != null) {
          return fork.getFullPath();
        }
      }
      catch (IOException e) {
        GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, e);
      }
    }

    return null;
  }

  private static boolean isRepoOwner(@NotNull String user, @NotNull GithubRepo repo) {
    return StringUtil.equalsIgnoreCase(user, repo.getUserName());
  }

  private static void initLoadAvailableBranches(@NotNull final Project project,
                                                @NotNull final GithubInfo info,
                                                @Nullable final GithubFullPath upstreamPath,
                                                @NotNull final GithubCreatePullRequestDialog dialog) {
    dialog.setBusy(true);
    new SwingWorker<List<String>, Void>() {
      @Override
      protected List<String> doInBackground() throws Exception {
        List<String> result = new ArrayList<String>();
        try {
          final GithubAuthData auth = info.getAuthData();
          final GithubRepoDetailed repo = info.getRepo();
          final GithubRepo parent = repo.getParent();
          final GithubRepo source = repo.getSource();

          result.addAll(getBranches(auth, repo.getUserName(), repo.getName()));

          if (parent != null) {
            result.addAll(getBranches(auth, parent.getUserName(), parent.getName()));
          }

          if (source != null && !equals(source, parent)) {
            result.addAll(getBranches(auth, source.getUserName(), source.getName()));
          }

          if (upstreamPath != null && !equals(upstreamPath, repo) && !equals(upstreamPath, parent) && !equals(upstreamPath, source)) {
            result.addAll(getBranches(auth, upstreamPath.getUser(), upstreamPath.getRepository()));
          }
        }
        catch (IOException e) {
          GithubNotifications.showError(project, "Can't load available branches", e);
        }
        return result;
      }

      @Override
      protected void done() {
        try {
          dialog.addBranches(get());
        }
        catch (Exception ignore) {
        }
        dialog.setBusy(false);
      }

      @NotNull
      private List<String> getBranches(@NotNull GithubAuthData auth, @NotNull final String user, @NotNull String repo) throws IOException {
        List<GithubBranch> branches = GithubApiUtil.getRepoBranches(auth, user, repo);
        return ContainerUtil.map(branches, new Function<GithubBranch, String>() {
          @Override
          public String fun(GithubBranch branch) {
            return user + ":" + branch.getName();
          }
        });
      }

      private boolean equals(@NotNull GithubRepo repo1, @Nullable GithubRepo repo2) {
        if (repo2 == null) {
          return false;
        }
        return StringUtil.equalsIgnoreCase(repo1.getUserName(), repo2.getUserName());
      }

      private boolean equals(@NotNull GithubFullPath repo1, @Nullable GithubRepo repo2) {
        if (repo2 == null) {
          return false;
        }
        return StringUtil.equalsIgnoreCase(repo1.getUser(), repo2.getUserName());
      }
    }.execute();
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
}

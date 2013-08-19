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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TabbedPaneImpl;
import com.intellij.util.Consumer;
import com.intellij.util.Function;

import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
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
import git4idea.ui.GitCommitListPanel;
import icons.Git4ideaIcons;
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

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

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

    final GitRepository repository = GithubUtil.getGitRepository(project, file);
    if (repository == null) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Can't find git repository");
      return;
    }
    repository.update();

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

    final GithubInfo info = loadGithubInfoWithModal(project, userAndRepo, upstreamUserAndRepo);
    if (info == null) {
      return;
    }
    final Set<RemoteBranch> branches = getAvailableBranchesFromGit(repository);
    branches.addAll(info.getBranches());

    GithubRepo parent = info.getRepo().getParent();
    String suggestedBranch = parent == null ? null : parent.getUserName() + ":" + parent.getDefaultBranch();
    Collection<String> suggestions = ContainerUtil.map(branches, new Function<RemoteBranch, String>() {
      @Override
      public String fun(RemoteBranch remoteBranch) {
        return remoteBranch.getReference();
      }
    });
    Consumer<String> showDiff = new Consumer<String>() {
      @Override
      public void consume(String s) {
        showDiffByRef(project, s, branches, repository, currentBranch.getName());
      }
    };
    final GithubCreatePullRequestDialog dialog = new GithubCreatePullRequestDialog(project, suggestions, suggestedBranch, showDiff);
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

        String from = info.getRepo().getUserName() + ":" + currentBranch.getName();
        String onto = dialog.getTargetBranch();
        GithubAuthData auth = info.getAuthData();

        GithubFullPath targetRepo = findTargetRepository(project, auth, onto, info.getRepo(), upstreamUserAndRepo, branches);
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
  private static GithubInfo loadGithubInfoWithModal(@NotNull final Project project,
                                                    @NotNull final GithubFullPath userAndRepo,
                                                    @Nullable final GithubFullPath upstreamUserAndRepo) {
    try {
      return GithubUtil
        .computeValueInModal(project, "Access to GitHub", new ThrowableConvertor<ProgressIndicator, GithubInfo, IOException>() {
          @Override
          public GithubInfo convert(ProgressIndicator indicator) throws IOException {
            final Ref<GithubRepoDetailed> reposRef = new Ref<GithubRepoDetailed>();
            final GithubAuthData auth =
              GithubUtil.runAndGetValidAuth(project, indicator, new ThrowableConsumer<GithubAuthData, IOException>() {
                @Override
                public void consume(GithubAuthData authData) throws IOException {
                  reposRef.set(GithubApiUtil.getDetailedRepoInfo(authData, userAndRepo.getUser(), userAndRepo.getRepository()));
                }
              });
            List<RemoteBranch> branches = loadAvailableBranchesFromGithub(project, auth, reposRef.get(), upstreamUserAndRepo);
            return new GithubInfo(auth, reposRef.get(), branches);
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
  private static GithubFullPath findTargetRepository(@NotNull Project project,
                                                     @NotNull GithubAuthData auth,
                                                     @NotNull String onto,
                                                     @NotNull GithubRepoDetailed repo,
                                                     @Nullable GithubFullPath upstreamPath,
                                                     @NotNull Collection<RemoteBranch> branches) {
    String targetUser = onto.substring(0, onto.indexOf(':'));
    @Nullable GithubRepo parent = repo.getParent();
    @Nullable GithubRepo source = repo.getSource();

    for (RemoteBranch branch : branches) {
      if (StringUtil.equalsIgnoreCase(targetUser, branch.getUser()) && branch.getRepo() != null) {
        return new GithubFullPath(branch.getUser(), branch.getRepo());
      }
    }

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

  @NotNull
  private static Set<RemoteBranch> getAvailableBranchesFromGit(@NotNull GitRepository gitRepository) {
    Set<RemoteBranch> result = new HashSet<RemoteBranch>();
    for (GitRemoteBranch remoteBranch : gitRepository.getBranches().getRemoteBranches()) {
      for (String url : remoteBranch.getRemote().getUrls()) {
        if (GithubUrlUtil.isGithubUrl(url)) {
          GithubFullPath path = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url);
          if (path != null) {
            result.add(new RemoteBranch(path.getUser(), remoteBranch.getNameForRemoteOperations(), path.getRepository(),
                                        remoteBranch.getNameForLocalOperations()));
            break;
          }
        }
      }
    }
    return result;
  }

  @NotNull
  private static List<RemoteBranch> loadAvailableBranchesFromGithub(@NotNull final Project project,
                                                                    @NotNull final GithubAuthData auth,
                                                                    @NotNull final GithubRepoDetailed repo,
                                                                    @Nullable final GithubFullPath upstreamPath) {
    List<RemoteBranch> result = new ArrayList<RemoteBranch>();
    try {
      final GithubRepo parent = repo.getParent();
      final GithubRepo source = repo.getSource();

      if (parent != null) {
        result.addAll(getBranches(auth, parent.getUserName(), parent.getName()));
      }

      result.addAll(getBranches(auth, repo.getUserName(), repo.getName()));

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

  @NotNull
  private static List<RemoteBranch> getBranches(@NotNull GithubAuthData auth, @NotNull final String user, @NotNull final String repo)
    throws IOException {
    List<GithubBranch> branches = GithubApiUtil.getRepoBranches(auth, user, repo);
    return ContainerUtil.map(branches, new Function<GithubBranch, RemoteBranch>() {
      @Override
      public RemoteBranch fun(GithubBranch branch) {
        return new RemoteBranch(user, branch.getName(), repo);
      }
    });
  }

  private static boolean equals(@NotNull GithubRepo repo1, @Nullable GithubRepo repo2) {
    if (repo2 == null) {
      return false;
    }
    return StringUtil.equalsIgnoreCase(repo1.getUserName(), repo2.getUserName());
  }

  private static boolean equals(@NotNull GithubFullPath repo1, @Nullable GithubRepo repo2) {
    if (repo2 == null) {
      return false;
    }
    return StringUtil.equalsIgnoreCase(repo1.getUser(), repo2.getUserName());
  }

  private static void showDiffByRef(@NotNull Project project,
                                    @Nullable String ref,
                                    @NotNull Set<RemoteBranch> branches,
                                    @NotNull GitRepository gitRepository,
                                    @NotNull String currentBranch) {
    RemoteBranch branch = findRemoteBranch(branches, ref);
    if (branch == null || branch.getLocalBranch() == null) {
      GithubNotifications.showErrorDialog(project, "Can't show diff", "Can't find local branch");
      return;
    }
    String targetBranch = branch.getLocalBranch();

    DiffInfo info = getDiffInfo(project, gitRepository, currentBranch, targetBranch);
    if (info == null) {
      GithubNotifications.showErrorDialog(project, "Can't show diff", "Can't get diff info");
      return;
    }

    GithubCreatePullRequestDiffDialog dialog = new GithubCreatePullRequestDiffDialog(project, info);
    dialog.show();
  }

  @Nullable
  private static RemoteBranch findRemoteBranch(@NotNull Set<RemoteBranch> branches, @Nullable String ref) {
    if (ref == null) {
      return null;
    }
    List<String> list = StringUtil.split(ref, ":");
    if (list.size() != 2) {
      return null;
    }
    for (RemoteBranch branch : branches) {
      if (StringUtil.equalsIgnoreCase(list.get(0), branch.getUser()) && StringUtil.equals(list.get(1), branch.getBranch())) {
        return branch;
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
      return GithubUtil.computeValueInModal(project, "Access to Git", new ThrowableConvertor<ProgressIndicator, DiffInfo, VcsException>() {
        @Override
        public DiffInfo convert(ProgressIndicator indicator) throws VcsException {
          List<GitCommit> commits = GitHistoryUtils.history(project, repository.getRoot(), targetBranch + "..");
          Collection<Change> diff =
            GitChangeUtils.getDiff(repository.getProject(), repository.getRoot(), targetBranch, currentBranch, null);
          return new DiffInfo(targetBranch, currentBranch, commits, diff);
        }
      });
    }
    catch (VcsException e) {
      LOG.info(e);
      return null;
    }
  }

  private static class GithubCreatePullRequestDiffDialog extends DialogWrapper {
    @NotNull private final Project myProject;
    @NotNull private final DiffInfo myInfo;
    private JPanel myLogPanel;

    public GithubCreatePullRequestDiffDialog(@NotNull Project project, @NotNull DiffInfo info) {
      super(project, false);
      myProject = project;
      myInfo = info;
      setTitle(String.format("Comparing %s with %s", info.getFrom(), info.getTo()));
      setModal(false);
      init();
    }

    @Override
    protected JComponent createCenterPanel() {
      myLogPanel = new GithubCreatePullRequestLogPanel(myProject, myInfo);
      JPanel diffPanel = new GithubCreatePullRequestDiffPanel(myProject, myInfo);

      TabbedPaneImpl tabbedPane = new TabbedPaneImpl(SwingConstants.TOP);
      tabbedPane.addTab("Log", Git4ideaIcons.Branch, myLogPanel);
      tabbedPane.addTab("Diff", AllIcons.Actions.Diff, diffPanel);
      tabbedPane.setKeyboardNavigation(TabbedPaneImpl.DEFAULT_PREV_NEXT_SHORTCUTS);
      return tabbedPane;
    }

    @NotNull
    @Override
    protected Action[] createActions() {
      return new Action[0];
    }

    @Override
    protected String getDimensionServiceKey() {
      return "Github.CreatePullRequestDiffDialog";
    }
  }

  private static class GithubCreatePullRequestDiffPanel extends JPanel {

    private final Project myProject;
    private final DiffInfo myInfo;

    public GithubCreatePullRequestDiffPanel(@NotNull Project project, @NotNull DiffInfo info) {
      super(new BorderLayout(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP));
      myProject = project;
      myInfo = info;

      add(createCenterPanel());
    }

    private JComponent createCenterPanel() {
      List<Change> diff = new ArrayList<Change>(myInfo.getDiff());
      final ChangesBrowser changesBrowser =
        new ChangesBrowser(myProject, null, diff, null, false, true, null, ChangesBrowser.MyUseCase.COMMITTED_CHANGES, null);
      changesBrowser.setChangesToDisplay(diff);
      return changesBrowser;
    }
  }

  private static class GithubCreatePullRequestLogPanel extends JPanel {
    private final Project myProject;
    private final DiffInfo myInfo;

    private GitCommitListPanel myCommitPanel;

    GithubCreatePullRequestLogPanel(@NotNull Project project, @NotNull DiffInfo info) {
      super(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
      myProject = project;
      myInfo = info;

      add(createCenterPanel());
    }

    private JComponent createCenterPanel() {
      final ChangesBrowser changesBrowser = new ChangesBrowser(myProject, null, Collections.<Change>emptyList(), null, false, true, null,
                                                               ChangesBrowser.MyUseCase.COMMITTED_CHANGES, null);

      myCommitPanel =
        new GitCommitListPanel(myInfo.getCommits(), String.format("Branch %s is fully merged to %s", myInfo.getFrom(), myInfo.getTo()));
      addSelectionListener(myCommitPanel, changesBrowser);

      myCommitPanel.registerDiffAction(changesBrowser.getDiffAction());

      Splitter rootPanel = new Splitter(false, 0.7f);
      rootPanel.setSecondComponent(changesBrowser);
      rootPanel.setFirstComponent(myCommitPanel);

      return rootPanel;
    }

    private static void addSelectionListener(@NotNull GitCommitListPanel sourcePanel, @NotNull final ChangesBrowser changesBrowser) {
      sourcePanel.addListSelectionListener(new Consumer<GitCommit>() {
        @Override
        public void consume(GitCommit commit) {
          changesBrowser.setChangesToDisplay(commit.getChanges());
        }
      });
    }

  }

  private static class RemoteBranch {
    @NotNull final String myUser;
    @NotNull final String myBranch;

    @Nullable final String myRepo;
    @Nullable final String myLocalBranch;

    private RemoteBranch(@NotNull String user, @NotNull String branch) {
      this(user, branch, null, null);
    }

    private RemoteBranch(@NotNull String user, @NotNull String branch, @NotNull String repo) {
      this(user, branch, repo, null);
    }

    public RemoteBranch(@NotNull String user, @NotNull String branch, @Nullable String repo, @Nullable String localBranch) {
      myUser = user;
      myBranch = branch;
      myRepo = repo;
      myLocalBranch = localBranch;
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
    public String getRepo() {
      return myRepo;
    }

    @Nullable
    public String getLocalBranch() {
      return myLocalBranch;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      RemoteBranch that = (RemoteBranch)o;

      if (!StringUtil.equalsIgnoreCase(myUser, that.myUser)) return false;
      if (!StringUtil.equalsIgnoreCase(myBranch, that.myBranch)) return false;

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
    @NotNull private final List<RemoteBranch> myBranches;

    private GithubInfo(@NotNull GithubAuthData authData, @NotNull GithubRepoDetailed repo, @NotNull List<RemoteBranch> branches) {
      myAuthData = authData;
      myRepo = repo;
      myBranches = branches;
    }

    @NotNull
    public GithubRepoDetailed getRepo() {
      return myRepo;
    }

    @NotNull
    public GithubAuthData getAuthData() {
      return myAuthData;
    }

    @NotNull
    public List<RemoteBranch> getBranches() {
      return myBranches;
    }
  }

  private static class DiffInfo {
    @NotNull private final List<GitCommit> commits;
    @NotNull private final Collection<Change> diff;
    @NotNull private final String from;
    @NotNull private final String to;

    private DiffInfo(@NotNull String from, @NotNull String to, @NotNull List<GitCommit> commits, @NotNull Collection<Change> diff) {
      this.commits = commits;
      this.diff = diff;
      this.from = from;
      this.to = to;
    }

    @NotNull
    public List<GitCommit> getCommits() {
      return commits;
    }

    @NotNull
    public Collection<Change> getDiff() {
      return diff;
    }

    @NotNull
    public String getFrom() {
      return from;
    }

    @NotNull
    public String getTo() {
      return to;
    }
  }
}

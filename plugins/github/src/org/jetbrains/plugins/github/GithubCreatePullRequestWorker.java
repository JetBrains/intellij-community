package org.jetbrains.plugins.github;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.*;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationCanceledException;
import org.jetbrains.plugins.github.ui.GithubSelectForkDialog;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestWorker {
  private static final Logger LOG = GithubUtil.LOG;
  private static final String CANNOT_CREATE_PULL_REQUEST = "Can't create pull request";

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final GitRepository myGitRepository;
  @NotNull private final GithubFullPath myPath;
  @NotNull private final String myRemoteName;
  @NotNull private final String myRemoteUrl;
  @NotNull private final String myCurrentBranch;
  @NotNull private final GithubAuthData myAuth;

  private volatile GithubFullPath myForkPath;
  private volatile String myTargetRemote;

  private GithubCreatePullRequestWorker(@NotNull Project project,
                                        @NotNull Git git,
                                        @NotNull GitRepository gitRepository,
                                        @NotNull GithubFullPath path,
                                        @NotNull String remoteName,
                                        @NotNull String remoteUrl,
                                        @NotNull String currentBranch,
                                        @NotNull GithubAuthData auth) {
    myProject = project;
    myGit = git;
    myGitRepository = gitRepository;
    myPath = path;
    myRemoteName = remoteName;
    myRemoteUrl = remoteUrl;
    myCurrentBranch = currentBranch;
    myAuth = auth;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Nullable
  public static GithubCreatePullRequestWorker createPullRequestWorker(@NotNull final Project project, @Nullable final VirtualFile file) {
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

    GithubAuthData auth;
    try {
      auth = GithubUtil
        .computeValueInModal(project, "Access to GitHub", new ThrowableConvertor<ProgressIndicator, GithubAuthData, IOException>() {
          @Override
          public GithubAuthData convert(ProgressIndicator indicator) throws IOException {
            return GithubUtil.getValidAuthDataFromConfig(project, indicator);
          }
        });
    }
    catch (GithubAuthenticationCanceledException e) {
      return null;
    }
    catch (IOException e) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, e);
      return null;
    }

    return new GithubCreatePullRequestWorker(project, git, gitRepository, path, remoteName, remoteUrl, currentBranch.getName(), auth);
  }

  @Nullable
  public GithubTargetInfo setTarget(@NotNull final GithubFullPath forkPath) {
    try {
      GithubInfo info =
        GithubUtil.computeValueInModal(myProject, "Access to GitHub", new ThrowableConvertor<ProgressIndicator, GithubInfo, IOException>() {
          @Override
          public GithubInfo convert(ProgressIndicator indicator) throws IOException {
            // configure remote
            GitRemote targetRemote = GithubUtil.findGithubRemote(myGitRepository, forkPath);
            String targetRemoteName = targetRemote == null ? null : targetRemote.getName();
            if (targetRemoteName == null) {
              final AtomicReference<Integer> responseRef = new AtomicReference<Integer>();
              ApplicationManager.getApplication().invokeAndWait(new Runnable() {
                @Override
                public void run() {
                  responseRef.set(GithubNotifications
                                    .showYesNoDialog(myProject, "Can't find remote", "Configure remote for '" + forkPath.getUser() + "'?"));
                }
              }, indicator.getModalityState());
              if (responseRef.get() == Messages.YES) {
                targetRemoteName = configureRemote(myProject, myGitRepository, forkPath);
              }
            }

            // load available branches
            List<String> branches = ContainerUtil.map(GithubApiUtil.getRepoBranches(myAuth, forkPath.getUser(), forkPath.getRepository()),
                                                      new Function<GithubBranch, String>() {
                                                        @Override
                                                        public String fun(GithubBranch githubBranch) {
                                                          return githubBranch.getName();
                                                        }
                                                      });

            // fetch
            if (targetRemoteName != null) {
              GitFetchResult result = new GitFetcher(myProject, indicator, false).fetch(myGitRepository.getRoot(), targetRemoteName, null);
              if (!result.isSuccess()) {
                GitFetcher.displayFetchResult(myProject, result, null, result.getErrors());
                targetRemoteName = null;
              }
            }

            return new GithubInfo(branches, targetRemoteName);
          }
        });

      myForkPath = forkPath;
      myTargetRemote = info.getTargetRemote();
      return new GithubTargetInfo(info.getBranches(), myTargetRemote != null);
    }
    catch (GithubAuthenticationCanceledException e) {
      return null;
    }
    catch (IOException e) {
      GithubNotifications.showErrorDialog(myProject, CANNOT_CREATE_PULL_REQUEST, e);
      return null;
    }
  }

  public void showDiffDialog(@NotNull String branch) {
    if (myTargetRemote != null) {
      showDiffByRef(myProject, branch, myGitRepository, myTargetRemote, myCurrentBranch);
    }
  }

  @Nullable
  public GithubFullPath showTargetDialog() {
    final GithubInfo2 info = getAvailableForksInModal(myProject, myGitRepository, myAuth, myPath);
    if (info == null) {
      return null;
    }

    Convertor<String, GithubFullPath> getForkPath = new Convertor<String, GithubFullPath>() {
      @Nullable
      @Override
      public GithubFullPath convert(@NotNull final String user) {
        return GithubUtil.computeValueInModal(myProject, "Access to GitHub", new Convertor<ProgressIndicator, GithubFullPath>() {
          @Nullable
          @Override
          public GithubFullPath convert(ProgressIndicator o) {
            return findRepositoryByUser(myProject, user, info.getForks(), myAuth, info.getSource());
          }
        });
      }
    };
    GithubSelectForkDialog dialog = new GithubSelectForkDialog(myProject, info.getForks(), getForkPath);
    dialog.show();
    if (!dialog.isOK()) {
      return null;
    }
    return dialog.getPath();
  }

  public void performAction(@NotNull final String title, @NotNull final String description, @NotNull final String targetBranch) {
    new Task.Backgroundable(myProject, "Creating pull request...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        LOG.info("Pushing current branch");
        indicator.setText("Pushing current branch...");
        GitCommandResult result = myGit.push(myGitRepository, myRemoteName, myRemoteUrl, myCurrentBranch, true);
        if (!result.success()) {
          GithubNotifications.showError(myProject, CANNOT_CREATE_PULL_REQUEST, "Push failed:<br/>" + result.getErrorOutputAsHtmlString());
          return;
        }

        String baseBranch = myPath.getUser() + ":" + myCurrentBranch;

        LOG.info("Creating pull request");
        indicator.setText("Creating pull request...");
        GithubPullRequest request = createPullRequest(myProject, myAuth, myForkPath, title, description, baseBranch, targetBranch);
        if (request == null) {
          return;
        }

        GithubNotifications
          .showInfoURL(myProject, "Successfully created pull request", "Pull Request #" + request.getNumber(), request.getHtmlUrl());
      }
    }.queue();
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

  @Nullable
  private static GithubInfo2 getAvailableForksInModal(@NotNull final Project project,
                                                      @NotNull final GitRepository gitRepository,
                                                      @NotNull final GithubAuthData auth,
                                                      @NotNull final GithubFullPath path) {
    return GithubUtil.computeValueInModal(project, "Access to GitHub", new Convertor<ProgressIndicator, GithubInfo2>() {
      @Nullable
      @Override
      public GithubInfo2 convert(ProgressIndicator indicator) {
        try {
          final Set<GithubFullPath> forks = new HashSet<GithubFullPath>();

          // GitHub
          GithubRepoDetailed repo = GithubApiUtil.getDetailedRepoInfo(auth, path.getUser(), path.getRepository());
          forks.add(path);
          if (repo.getParent() != null) {
            forks.add(repo.getParent().getFullPath());
          }
          if (repo.getSource() != null) {
            forks.add(repo.getSource().getFullPath());
          }

          // Git
          forks.addAll(getAvailableForksFromGit(gitRepository));

          GithubRepo forkTreeRoot = repo.getSource() == null ? repo : repo.getSource();
          return new GithubInfo2(forks, forkTreeRoot);
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
    @Nullable private final String myTargetRemote;

    private GithubInfo(@NotNull List<String> repo, @Nullable String targetRemote) {
      myBranches = repo;
      myTargetRemote = targetRemote;
    }

    @NotNull
    public List<String> getBranches() {
      return myBranches;
    }

    @Nullable
    public String getTargetRemote() {
      return myTargetRemote;
    }
  }

  private static class GithubInfo2 {
    @NotNull private final Set<GithubFullPath> myForks;
    @NotNull private final GithubRepo mySource;

    private GithubInfo2(@NotNull Set<GithubFullPath> forks, @NotNull GithubRepo source) {
      myForks = forks;
      mySource = source;
    }

    @NotNull
    public Set<GithubFullPath> getForks() {
      return myForks;
    }

    @NotNull
    public GithubRepo getSource() {
      return mySource;
    }
  }

  public static class GithubTargetInfo {
    @NotNull private final List<String> myBranches;
    private final boolean myCanShowDiff;

    private GithubTargetInfo(@NotNull List<String> branches, boolean canShowDiff) {
      myBranches = branches;
      myCanShowDiff = canShowDiff;
    }

    @NotNull
    public List<String> getBranches() {
      return myBranches;
    }

    public boolean isCanShowDiff() {
      return myCanShowDiff;
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

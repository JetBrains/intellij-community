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
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.containers.*;
import com.intellij.util.containers.HashMap;
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
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestWorker {
  private static final Logger LOG = GithubUtil.LOG;
  private static final String CANNOT_CREATE_PULL_REQUEST = "Can't Create Pull Request";

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final GitRepository myGitRepository;
  @NotNull private final GithubFullPath myPath;
  @NotNull private final String myRemoteName;
  @NotNull private final String myRemoteUrl;
  @NotNull private final String myCurrentBranch;
  @NotNull private final GithubAuthData myAuth;

  @NotNull private final Map<String, FutureTask<DiffInfo>> myDiffInfos;

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

    myDiffInfos = new HashMap<String, FutureTask<DiffInfo>>();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public String getCurrentBranch() {
    return myCurrentBranch;
  }

  public boolean canShowDiff() {
    return myTargetRemote != null;
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
                                    .showYesNoDialog(myProject, "Can't Find Remote", "Configure remote for '" + forkPath.getUser() + "'?"));
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

      myDiffInfos.clear();
      if (canShowDiff()) {
        for (final String branch : info.getBranches()) {
          myDiffInfos.put(branch, new FutureTask<DiffInfo>(new Callable<DiffInfo>() {
            @Override
            public DiffInfo call() throws Exception {
              return loadDiffInfo(myProject, myGitRepository, myCurrentBranch, myTargetRemote + "/" + branch);
            }
          }));
        }
      }

      return new GithubTargetInfo(info.getBranches());
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
    if (canShowDiff()) {
      DiffInfo info = getDiffInfoWithModal(branch);
      if (info == null) {
        GithubNotifications.showErrorDialog(myProject, "Can't Show Diff", "Can't get diff info");
        return;
      }

      GitCompareBranchesDialog dialog =
        new GitCompareBranchesDialog(myProject, info.getTo(), info.getFrom(), info.getInfo(), myGitRepository);
      dialog.show();
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
    DialogManager.show(dialog);
    if (!dialog.isOK()) {
      return null;
    }
    return dialog.getPath();
  }

  public boolean checkAction(@NotNull String targetBranch) {
    DiffInfo info = getDiffInfoWithModal(targetBranch);
    if (info == null) {
      return true;
    }
    if (info.getInfo().getBranchToHeadCommits(myGitRepository).isEmpty()) {
      GithubNotifications.showWarningDialog(myProject, CANNOT_CREATE_PULL_REQUEST,
        "Can't create empty pull request: the branch" + getCurrentBranch() + " in fully merged to the branch " + targetBranch + ".");
      return false;
    }
    if (info.getInfo().getHeadToBranchCommits(myGitRepository).isEmpty()) {
      return GithubNotifications
               .showYesNoDialog(myProject, "The branch" + targetBranch + " in not fully merged to the branch " + getCurrentBranch(),
                                "Do you want to proceed anyway?") == Messages.YES;
    }

    return true;
  }

  public void performAction(@NotNull final String title, @NotNull final String description, @NotNull final String targetBranch) {
    @NotNull final Project project = myProject;

    new Task.Backgroundable(myProject, "Creating pull request...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        LOG.info("Pushing current branch");
        indicator.setText("Pushing current branch...");
        GitCommandResult result = myGit.push(myGitRepository, myRemoteName, myRemoteUrl, myCurrentBranch, true);
        if (!result.success()) {
          GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, "Push failed:<br/>" + result.getErrorOutputAsHtmlString());
          return;
        }

        String headBranch = myPath.getUser() + ":" + myCurrentBranch;

        LOG.info("Creating pull request");
        indicator.setText("Creating pull request...");
        GithubPullRequest request = createPullRequest(project, myAuth, myForkPath, title, description, headBranch, targetBranch);
        if (request == null) {
          return;
        }

        GithubNotifications
          .showInfoURL(project, "Successfully created pull request", "Pull request #" + request.getNumber(), request.getHtmlUrl());
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
                                                     @NotNull String head,
                                                     @NotNull String base) {
    try {
      return GithubApiUtil.createPullRequest(auth, targetRepo.getUser(), targetRepo.getRepository(), title, description, head, base);
    }
    catch (IOException e) {
      GithubNotifications.showError(project, CANNOT_CREATE_PULL_REQUEST, e);
      return null;
    }
  }

  @Nullable
  private DiffInfo getDiffInfo(@NotNull String branch) {
    try {
      FutureTask<DiffInfo> future = myDiffInfos.get(branch);
      if (future == null) {
        return null;
      }
      future.run();
      return future.get();
    }
    catch (InterruptedException e) {
      LOG.error(e);
      return null;
    }
    catch (ExecutionException e) {
      LOG.error(e);
      return null;
    }
  }

  @Nullable
  private DiffInfo getDiffInfoWithModal(@NotNull final String branch) {
    return GithubUtil.computeValueInModal(myProject, "Collecting diff data...", new Convertor<ProgressIndicator, DiffInfo>() {
      @Override
      @Nullable
      public DiffInfo convert(ProgressIndicator indicator) {
        return getDiffInfo(branch);
      }
    });
  }

  public void getDiffDescriptionInPooledThread(@NotNull final String branch, @NotNull final Consumer<DiffDescription> after) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        after.consume(getDefaultDescriptionMessage(branch, getDiffInfo(branch), myGitRepository));
      }
    });
  }

  @Nullable
  private static DiffInfo loadDiffInfo(@NotNull final Project project,
                                       @NotNull final GitRepository repository,
                                       @NotNull final String currentBranch,
                                       @NotNull final String targetBranch) {
    try {
      List<GitCommit> commits1 = GitHistoryUtils.history(project, repository.getRoot(), ".." + targetBranch);
      List<GitCommit> commits2 = GitHistoryUtils.history(project, repository.getRoot(), targetBranch + "..");
      Collection<Change> diff = GitChangeUtils.getDiff(repository.getProject(), repository.getRoot(), targetBranch, currentBranch, null);
      GitCommitCompareInfo info = new GitCommitCompareInfo(GitCommitCompareInfo.InfoType.BRANCH_TO_HEAD);
      info.put(repository, diff);
      info.put(repository, Pair.create(commits1, commits2));
      return new DiffInfo(info, currentBranch, targetBranch);
    }
    catch (VcsException e) {
      LOG.info(e);
      return null;
    }
  }

  @NotNull
  private static DiffDescription getDefaultDescriptionMessage(@NotNull String branch,
                                                              @Nullable DiffInfo info,
                                                              @NotNull GitRepository gitRepository) {
    if (info == null) {
      return new DiffDescription(branch, null, null);
    }

    if (info.getInfo().getBranchToHeadCommits(gitRepository).size() != 1) {
      return new DiffDescription(branch, info.getFrom(), null);
    }

    GitCommit commit = info.getInfo().getBranchToHeadCommits(gitRepository).get(0);
    return new DiffDescription(branch, commit.getSubject(), commit.getFullMessage());
  }

  @Nullable
  private static GithubInfo2 getAvailableForksInModal(@NotNull final Project project,
                                                      @NotNull final GitRepository gitRepository,
                                                      @NotNull final GithubAuthData auth,
                                                      @NotNull final GithubFullPath path) {
    try {
      return GithubUtil
        .computeValueInModal(project, "Access to GitHub", new ThrowableConvertor<ProgressIndicator, GithubInfo2, IOException>() {
          @Override
          public GithubInfo2 convert(ProgressIndicator indicator) throws IOException {
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

    private GithubTargetInfo(@NotNull List<String> branches) {
      myBranches = branches;
    }

    @NotNull
    public List<String> getBranches() {
      return myBranches;
    }
  }

  private static class DiffInfo {
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

  public static class DiffDescription {
    @NotNull private final String myBranch;
    @Nullable private final String myTitle;
    @Nullable private final String myDescription;

    public DiffDescription(@NotNull String branch, @Nullable String title, @Nullable String description) {
      myBranch = branch;
      myTitle = title;
      myDescription = description;
    }

    @NotNull
    public String getBranch() {
      return myBranch;
    }

    @Nullable
    public String getTitle() {
      return myTitle;
    }

    @Nullable
    public String getDescription() {
      return myDescription;
    }
  }
}

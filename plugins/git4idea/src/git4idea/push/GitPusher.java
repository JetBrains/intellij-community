/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.push;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.*;
import git4idea.commands.GitCommandResult;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.GitUIUtil;
import git4idea.update.GitUpdateProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static git4idea.ui.GitUIUtil.code;

/**
 * Collects information to push and performs the push.
 *
 * @author Kirill Likhodedov
 */
public final class GitPusher {

  private final Project myProject;
  private final Collection<GitRepository> myRepositories;

  public GitPusher(Project project) {
    myProject = project;
    myRepositories = GitRepositoryManager.getInstance(project).getRepositories();
  }

  /**
   *
   *
   *
   * @param pushSpec which branches in which repositories should be pushed.
   *                               The most common situation is all repositories in the project with a single currently active branch for
   *                               each of them.
   * @return
   */
  @NotNull
  public GitCommitsByRepoAndBranch collectCommitsToPush(GitPushSpec pushSpec) {
    Map<GitRepository, List<GitPushSpec.SourceDest>> reposAndBranchesToPush = prepareRepositoriesAndBranchesToPush(pushSpec);
    
    Map<GitRepository, GitCommitsByBranch> commitsByRepoAndBranch = new HashMap<GitRepository, GitCommitsByBranch>();
    for (GitRepository repository : myRepositories) {
      GitCommitsByBranch commitsByBranch = collectsCommitsToPush(repository, reposAndBranchesToPush.get(repository));
      if (!commitsByBranch.isEmpty()) {
        commitsByRepoAndBranch.put(repository, commitsByBranch);
      }
    }
    return new GitCommitsByRepoAndBranch(commitsByRepoAndBranch);
  }

  private Map<GitRepository, List<GitPushSpec.SourceDest>> prepareRepositoriesAndBranchesToPush(GitPushSpec pushSpec) {
    Map<GitRepository, List<GitPushSpec.SourceDest>> res = new HashMap<GitRepository, List<GitPushSpec.SourceDest>>();
    for (GitRepository repository : myRepositories) {
      res.put(repository, pushSpec.parse(repository));
    }
    return res;
  }

  private GitCommitsByBranch collectsCommitsToPush(GitRepository repository, List<GitPushSpec.SourceDest> sourcesDestinations) {
    Map<GitBranch, List<GitCommit>> commitsByBranch = new HashMap<GitBranch, List<GitCommit>>();

    for (GitPushSpec.SourceDest sourceDest : sourcesDestinations) {
      GitReference source = sourceDest.getSource();
      List<GitCommit> commits = collectCommitsToPush(repository, source.getName(), sourceDest.getDest().getName());
      if (!commits.isEmpty()) {
        commitsByBranch.put((GitBranch)source, commits);
      }
      
    }

    return new GitCommitsByBranch(commitsByBranch);
  }

  @NotNull
  private List<GitCommit> collectCommitsToPush(GitRepository repository, String source, String destination) {
    try {
      return GitHistoryUtils.history(myProject, repository.getRoot(), destination + ".." + source);
    }
    catch (VcsException e) {
      e.printStackTrace();  // TODO
    }
    return Collections.emptyList();
  }
  
  public void push(@NotNull GitPushInfo pushInfo) {
    push(pushInfo, null);
  }

  private void push(@NotNull GitPushInfo pushInfo, @Nullable GitPushResult previousResult) {
    GitPushResult result = tryPushAndGetResult(pushInfo);
    handleResult(pushInfo, result, previousResult);
    GitRepositoryManager.getInstance(myProject).updateAllRepositories(GitRepository.TrackedTopic.ALL);
  }

  private static GitPushResult tryPushAndGetResult(@NotNull GitPushInfo pushInfo) {
    GitPushResult pushResult = new GitPushResult();
    
    GitCommitsByRepoAndBranch commits = pushInfo.getCommits();
    for (GitRepository repository : commits.getRepositories()) {
      GitPushRejectedDetector rejectedDetector = new GitPushRejectedDetector();
      GitCommandResult res = Git.push(repository, pushInfo.getPushSpec(), rejectedDetector);

      GitPushRepoResult repoResult;
      if (rejectedDetector.rejected()) {
        Collection<String> rejectedBranches = rejectedDetector.getRejectedBranches();
        Map<GitBranch, GitPushBranchResult> rejectedMap = new HashMap<GitBranch, GitPushBranchResult>();
        GitCommitsByBranch commitsByBranch = commits.get(repository);
        for (GitBranch branch : commitsByBranch.getBranches()) {
          rejectedMap.put(branch, branchInRejected(branch, rejectedBranches) ? GitPushBranchResult.REJECTED : GitPushBranchResult.SUCCESS);
        }
        repoResult = GitPushRepoResult.someRejected(rejectedMap, res);
      }
      else if (res.success()) {
        repoResult = GitPushRepoResult.success(res);
      }
      else {
        repoResult = GitPushRepoResult.error(res);
      }

      pushResult.append(repository, repoResult);
    }
    return pushResult;
  }

  private static boolean branchInRejected(GitBranch branch, Collection<String> rejectedBranches) {
    String branchName = branch.getName();
    for (String rejectedBranch : rejectedBranches) {
      if (rejectedBranch.equals(branchName)) {
        return true;
      }
    }
    return false;
  }

  // if all repos succeeded, show notification.
  // if all repos failed, show notification.
  // if some repos failed, show notification with both results.
  // if in failed repos, some branches were rejected, it is a warning-type, but not an error.
  // if in a failed repo, current branch was rejected, propose to update the branch. Don't do it if not current branch was rejected,
  // since it is difficult to update such a branch.
  // if in a failed repo, a branch was rejected that had nothing to push, don't notify about the rejection.
  // Besides all of the above, don't confuse users with 1 repository with all this "repository/root" stuff;
  // don't confuse users which push only a single branch with all this "branch" stuff.
  private void handleResult(@NotNull GitPushInfo pushInfo, @NotNull GitPushResult result, @Nullable GitPushResult previousResult) {
    result.mergeFrom(previousResult);

    if (result.isEmpty()) {
      return;
    }
    if (result.totalSuccess()) {
      int commitsPushed = pushInfo.getCommits().commitsNumber();
      String message = "Pushed " + commits(commitsPushed);
      GitVcs.NOTIFICATION_GROUP_ID.createNotification(message, NotificationType.INFORMATION).notify(myProject);
    }
    else if (result.wasError()) {
      // if there was an error on any repo, we won't propose to update even if current branch of a repo was rejected
      GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification("Push failed",
                                                             getResultDescriptionWithOptionalReposIndication(pushInfo, result),
                                                             NotificationType.ERROR, null).notify(myProject);
    }
    else {
      // there were no errors, but some rejected branches on some of the repositories
      // => for current branch propose to update and re-push it. For others just warn
      Map<GitRepository, GitBranch> rejectedPushesForCurrentBranch = getRejectedPushesForCurrentBranch(result);

      if (!rejectedPushesForCurrentBranch.isEmpty()) {

        final GitRejectedPushUpdateDialog dialog = new GitRejectedPushUpdateDialog(myProject, rejectedPushesForCurrentBranch.keySet());
        final AtomicInteger exitCode = new AtomicInteger();
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            dialog.show();
            exitCode.set(dialog.getExitCode());
          }
        });

        Set<VirtualFile> roots = getRootsToUpdate(rejectedPushesForCurrentBranch, dialog.updateAll());
        boolean pushAgain = false;
        switch (exitCode.get()) {
          case GitRejectedPushUpdateDialog.MERGE_EXIT_CODE:
            pushAgain = update(roots, true);
            break;
          case GitRejectedPushUpdateDialog.REBASE_EXIT_CODE:
            pushAgain = update(roots, false);
            break;
        }

        if (pushAgain) {
          GitPushInfo newPushInfo =
            new GitPushInfo(pushInfo.getCommits().retainAll(rejectedPushesForCurrentBranch), pushInfo.getPushSpec());
          GitPushResult adjustedPushResult = result.remove(rejectedPushesForCurrentBranch);
          push(newPushInfo, adjustedPushResult);
          return; // don't notify - next push will notify all results in compound
        }
      }
      GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification("Push rejected",
                                                             getResultDescriptionWithOptionalReposIndication(pushInfo, result),
                                                             NotificationType.WARNING, null).notify(myProject);
    }
  }

  private Set<VirtualFile> getRootsToUpdate(Map<GitRepository, GitBranch> rejectedPushesForCurrentBranch, boolean updateAllRoots) {
    Set<VirtualFile> roots = new HashSet<VirtualFile>();
    if (updateAllRoots) {
      for (GitRepository repository : myRepositories) {
        roots.add(repository.getRoot());
      }
    }
    else {
      for (GitRepository repository : rejectedPushesForCurrentBranch.keySet()) {
        roots.add(repository.getRoot());
      }
    }
    return roots;
  }

  private Map<GitRepository, GitBranch> getRejectedPushesForCurrentBranch(GitPushResult pushResult) {
    final Map<GitRepository, GitBranch> rejectedPushesForCurrentBranch = new HashMap<GitRepository, GitBranch>();
    for (Map.Entry<GitRepository, GitPushRepoResult> entry : pushResult.group().myRejectedResults.entrySet()) {
      GitRepository repository = entry.getKey();
      GitBranch currentBranch = repository.getCurrentBranch();
      if (currentBranch == null) {
        continue;
      }
      GitPushRepoResult repoResult = entry.getValue();
      GitPushBranchResult curBranchResult = repoResult.getBranchResults().get(currentBranch);
      if (curBranchResult != null && curBranchResult == GitPushBranchResult.REJECTED) {
        rejectedPushesForCurrentBranch.put(repository, currentBranch);
      }
    }
    return rejectedPushesForCurrentBranch;
  }

  private boolean update(Set<VirtualFile> rootsToUpdate, boolean merge) {
    return new GitUpdateProcess(myProject, new EmptyProgressIndicator(), rootsToUpdate, UpdatedFiles.create())
      .update(merge ? GitUpdateProcess.UpdateMethod.MERGE : GitUpdateProcess.UpdateMethod.REBASE);
  }

  /**
   * Constructs the HTML-formatted message from error outputs of failed repositories.
   * If there is only 1 repository in the project, just returns the error without writing the repository url (to avoid confusion for people
   * with only 1 root ever).
   * Otherwise adds repository URL to the error that repository produced.
   */
  @NotNull
  public String getResultDescriptionWithOptionalReposIndication(GitPushInfo pushInfo, GitPushResult pushResult) {
    GroupedResult groupedResult = pushResult.group();
    
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<GitRepository, GitPushRepoResult> entry : groupedResult.myErrorResults.entrySet()) {
      GitRepository repository = entry.getKey();
      GitPushRepoResult result = entry.getValue();
      sb.append("<p>");
      if (!GitUtil.justOneGitRepository(myProject)) {
        sb.append("<code>" + repository.getPresentableUrl() + "</code>:<br/>");
      }
      sb.append(result.getOutput().getErrorOutputAsHtmlString());
      sb.append("</p>");
    }

    if (!groupedResult.myRejectedResults.isEmpty()) {
      sb.append("Pushes to some branches were rejected: <br/>");
      for (Map.Entry<GitRepository, GitPushRepoResult> entry : groupedResult.myRejectedResults.entrySet()) {
        GitRepository repository = entry.getKey();
        GitPushRepoResult result = entry.getValue();
        if (!GitUtil.justOneGitRepository(myProject)) {
          sb.append("<code>" + repository.getPresentableUrl() + "</code>:<br/>");
        }
        sb.append(result.getBranchesDescription(pushInfo.getCommits().get(repository)));
        sb.append("</p>");
      }
    }
    
    if (!groupedResult.mySuccessfulResults.isEmpty()) {
      sb.append("Some pushes were successful: <br/>");
      for (Map.Entry<GitRepository, GitPushRepoResult> entry : groupedResult.mySuccessfulResults.entrySet()) {
        GitRepository repository = entry.getKey();
        GitPushRepoResult result = entry.getValue();
        if (!GitUtil.justOneGitRepository(myProject)) {
          sb.append("<code>" + repository.getPresentableUrl() + "</code>:<br/>");
        }
        sb.append(result.getPushedCommitsDescription(pushInfo.getCommits().get(repository)));
        sb.append("</p>");
      }
    }
    
    return sb.toString();
  }

  private static class GroupedResult {
    private final Map<GitRepository, GitPushRepoResult> mySuccessfulResults;
    private final Map<GitRepository, GitPushRepoResult> myErrorResults;
    private final Map<GitRepository, GitPushRepoResult> myRejectedResults;

    GroupedResult(Map<GitRepository, GitPushRepoResult> successfulResults,
                         Map<GitRepository, GitPushRepoResult> errorResults, Map<GitRepository, GitPushRepoResult> rejectedResults) {
      mySuccessfulResults = successfulResults;
      myErrorResults = errorResults;
      myRejectedResults = rejectedResults;
    }
  }

  private static class GitPushResult {
    private final Map<GitRepository, GitPushRepoResult> myResults = new HashMap<GitRepository, GitPushRepoResult>();
    
    void append(GitRepository repository, GitPushRepoResult result) {
      myResults.put(repository, result);
    }

    boolean totalSuccess() {
      for (GitPushRepoResult repoResult : myResults.values()) {
        if (repoResult.getType() != GitPushRepoResult.Type.SUCCESS) {
          return false;
        }
      }
      return true;
    }
    
    boolean wasError() {
      for (GitPushRepoResult repoResult : myResults.values()) {
        if (repoResult.getType() == GitPushRepoResult.Type.ERROR) {
          return true;
        }
      }
      return false;
    }

    GroupedResult group() {
      final Map<GitRepository, GitPushRepoResult> successfulResults = new HashMap<GitRepository, GitPushRepoResult>();
      final Map<GitRepository, GitPushRepoResult> errorResults = new HashMap<GitRepository, GitPushRepoResult>();
      final Map<GitRepository, GitPushRepoResult> rejectedResults = new HashMap<GitRepository, GitPushRepoResult>();

      for (Map.Entry<GitRepository, GitPushRepoResult> entry : myResults.entrySet()) {
        GitRepository repository = entry.getKey();
        GitPushRepoResult repoResult = entry.getValue();
        if (repoResult.getType() == GitPushRepoResult.Type.SUCCESS) {
          successfulResults.put(repository, repoResult);
        } else if (repoResult.getType() == GitPushRepoResult.Type.SOME_REJECTED) {
          rejectedResults.put(repository, repoResult);
        } else {
          errorResults.put(repository, repoResult);
        }
      }
      return new GroupedResult(successfulResults, errorResults, rejectedResults);
    }

    boolean isEmpty() {
      return myResults.isEmpty();
    }

    GitPushResult remove(Map<GitRepository, GitBranch> repoBranchPairs) {
      GitPushResult result = new GitPushResult();
      for (Map.Entry<GitRepository, GitPushRepoResult> entry : myResults.entrySet()) {
        GitRepository repository = entry.getKey();
        GitPushRepoResult repoResult = entry.getValue();
        if (repoBranchPairs.containsKey(repository)) {
          GitPushRepoResult adjustedResult = repoResult.remove(repoBranchPairs.get(repository));
          if (!repoResult.isEmpty()) {
            result.append(repository, adjustedResult);
          }
        } else {
          result.append(repository, repoResult);
        }
      }
      return result;
    }

    /**
     * Merges the given results to this result.
     * In the case of conflict (i.e. different results for a repository-branch pair), current result is preferred over the previous one.
     */
    void mergeFrom(@Nullable GitPushResult previousResult) {
      if (previousResult == null) {
        return;
      }

      for (Map.Entry<GitRepository, GitPushRepoResult> entry : previousResult.myResults.entrySet()) {
        GitRepository repository = entry.getKey();
        GitPushRepoResult repoResult = entry.getValue();
        if (myResults.containsKey(repository)) {
          myResults.get(repository).mergeFrom(previousResult.myResults.get(repository));
        } else {
          append(repository, repoResult);
        }
      }
    }
  }
  
  /**
   * If an error happens, all push is unsuccessful, for all branches.
   * Otherwise we've got separate results for branches.
   */
  private static class GitPushRepoResult {

    private enum Type {
      SUCCESS,
      SOME_REJECTED,
      ERROR
    }
    Type myType;
    GitCommandResult myOutput;
    Map<GitBranch, GitPushBranchResult> myBranchResults = new HashMap<GitBranch, GitPushBranchResult>();

    private GitPushRepoResult(Type type, GitCommandResult output) {
      myType = type;
      myOutput = output;
    }

    GitPushRepoResult(Map<GitBranch, GitPushBranchResult> resultsByBranch, GitCommandResult output) {
      this(Type.SOME_REJECTED, output);
      myBranchResults = resultsByBranch;
    }

    static GitPushRepoResult success(GitCommandResult output) {
      return new GitPushRepoResult(Type.SUCCESS, output);
    }

    static GitPushRepoResult error(GitCommandResult output) {
      return new GitPushRepoResult(Type.ERROR, output);
    }
    
    static GitPushRepoResult someRejected(Map<GitBranch, GitPushBranchResult> resultsByBranch, GitCommandResult output) {
      return new GitPushRepoResult(resultsByBranch, output);
    }

    Type getType() {
      return myType;
    }

    GitCommandResult getOutput() {
      return myOutput;
    }

    Map<GitBranch, GitPushBranchResult> getBranchResults() {
      return myBranchResults;
    }

    GitPushRepoResult remove(@NotNull GitBranch branch) {
      Map<GitBranch, GitPushBranchResult> resultsByBranch = new HashMap<GitBranch, GitPushBranchResult>();
      for (Map.Entry<GitBranch, GitPushBranchResult> entry : myBranchResults.entrySet()) {
        GitBranch b = entry.getKey();
        if (!b.equals(branch)) {
          resultsByBranch.put(b, entry.getValue());
        }
      }
      return new GitPushRepoResult(resultsByBranch, myOutput);
    }

    boolean isEmpty() {
      return myBranchResults.isEmpty();
    }

    /**
     * Merges the given results to this result.
     * In the case of conflict (i.e. different results for a branch), current result is preferred over the previous one.
     */
    public void mergeFrom(@NotNull GitPushRepoResult repoResult) {
      for (Map.Entry<GitBranch, GitPushBranchResult> entry : repoResult.myBranchResults.entrySet()) {
        GitBranch branch = entry.getKey();
        GitPushBranchResult branchResult = entry.getValue();
        if (!myBranchResults.containsKey(branch)) {   // otherwise current result is preferred
          myBranchResults.put(branch, branchResult);
        }
      }
    }

    public String getBranchesDescription(GitCommitsByBranch commitsByBranch) {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<GitBranch, GitPushBranchResult> entry : myBranchResults.entrySet()) {
        GitBranch branch = entry.getKey();
        GitPushBranchResult branchResult = entry.getValue();

        if (branchResult == GitPushBranchResult.SUCCESS) {
          sb.append(GitUIUtil.bold(branch.getName()) + ": pushed " + commits(pushedCommitsNum(commitsByBranch, branch))).append("<br/>");
        } else {
          sb.append(code(branch.getName())).append(": rejected").append("<br/>");
        }
      }
      return sb.toString();
    }

    public String getPushedCommitsDescription(GitCommitsByBranch commitsByBranch) {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<GitBranch, GitPushBranchResult> entry : myBranchResults.entrySet()) {
        GitBranch branch = entry.getKey();
        GitPushBranchResult branchResult = entry.getValue();

        if (branchResult == GitPushBranchResult.SUCCESS) {
          sb.append(branch.getName() + ": pushed " + commits(pushedCommitsNum(commitsByBranch, branch))).append("<br/>");
        }
      }      
      return sb.toString();
    }

    private static int pushedCommitsNum(GitCommitsByBranch commitsByBranch, GitBranch branch) {
      return commitsByBranch.get(branch).size();
    }
  }

  private enum GitPushBranchResult {
    SUCCESS,
    REJECTED
  }

  private static String commits(int commitNum) {
    return commitNum + " " + StringUtil.pluralize("commit", commitNum);
  }
}

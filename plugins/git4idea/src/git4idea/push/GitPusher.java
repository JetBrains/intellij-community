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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import git4idea.Git;
import git4idea.GitBranch;
import git4idea.GitReference;
import git4idea.GitVcs;
import git4idea.commands.GitCommandResult;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
    GitPushResult result = tryPushAndGetResult(pushInfo);
    handleResult(pushInfo, result);
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
  private void handleResult(@NotNull GitPushInfo pushInfo, @NotNull GitPushResult result) {
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

      GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification("Push rejected",
                                                             getResultDescriptionWithOptionalReposIndication(pushInfo, result),
                                                             NotificationType.WARNING, null).notify(myProject);

    }

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
      if (!onlyOneRepositoryInTheProject()) {
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
        if (!onlyOneRepositoryInTheProject()) {
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
        if (!onlyOneRepositoryInTheProject()) {
          sb.append("<code>" + repository.getPresentableUrl() + "</code>:<br/>");
        }
        sb.append(result.getPushedCommitsDescription(pushInfo.getCommits().get(repository)));
        sb.append("</p>");
      }
    }
    
    return sb.toString();
  }

  private boolean onlyOneRepositoryInTheProject() {
    return !GitRepositoryManager.getInstance(myProject).moreThanOneRoot();
  }
  
  private static class GroupedResult {
    private final Map<GitRepository, GitPushRepoResult> mySuccessfulResults;
    private final Map<GitRepository, GitPushRepoResult> myErrorResults;
    private final Map<GitRepository, GitPushRepoResult> myRejectedResults;

    public GroupedResult(Map<GitRepository, GitPushRepoResult> successfulResults,
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

    public GroupedResult group() {
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

    public boolean isEmpty() {
      return myResults.isEmpty();
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

    public GitPushRepoResult(Map<GitBranch, GitPushBranchResult> resultsByBranch, GitCommandResult output) {
      this(Type.SOME_REJECTED, output);
      myBranchResults = resultsByBranch;
    }

    public static GitPushRepoResult success(GitCommandResult output) {
      return new GitPushRepoResult(Type.SUCCESS, output);
    }

    public static GitPushRepoResult error(GitCommandResult output) {
      return new GitPushRepoResult(Type.ERROR, output);
    }
    
    public static GitPushRepoResult someRejected(Map<GitBranch, GitPushBranchResult> resultsByBranch, GitCommandResult output) {
      return new GitPushRepoResult(resultsByBranch, output);
    }

    public Type getType() {
      return myType;
    }

    public GitCommandResult getOutput() {
      return myOutput;
    }

    public String getBranchesDescription(GitCommitsByBranch commitsByBranch) {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<GitBranch, GitPushBranchResult> entry : myBranchResults.entrySet()) {
        GitBranch branch = entry.getKey();
        GitPushBranchResult branchResult = entry.getValue();

        if (branchResult == GitPushBranchResult.SUCCESS) {
          sb.append(bold(branch.getName()) + ": pushed " + commits(pushedCommitsNum(commitsByBranch, branch))).append("<br/>");
        } else {
          sb.append("<b>" + branch.getName() + "</b>: rejected").append("<br/>");
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
  
  private static String bold(String s) {
    return "<b>" + s + "</b>";
  }

}

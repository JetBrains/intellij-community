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

import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vcs.update.UpdateInfoTree;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import git4idea.GitBranch;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchUtil;
import git4idea.merge.MergeChangeCollector;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
* @author Kirill Likhodedov
*/
class GitPushResult {

  private static final Logger LOG = Logger.getInstance(GitPushResult.class);

  private final Project myProject;
  private final Map<GitRepository, GitPushRepoResult> myResults = new HashMap<GitRepository, GitPushRepoResult>();
  private final Map<GitRepository, GitRevisionNumber> myUpdateStarts = new HashMap<GitRepository, GitRevisionNumber>();
  private Label myBeforeUpdateLabel;

  GitPushResult(@NotNull Project project) {
    this(project, null, new HashMap<GitRepository, GitRevisionNumber>());
  }
  
  private GitPushResult(@NotNull Project project, @Nullable Label beforeUpdateLabel, @NotNull Map<GitRepository, GitRevisionNumber> updateStarts) {
    myProject = project;
    myBeforeUpdateLabel = beforeUpdateLabel;
    for (Map.Entry<GitRepository, GitRevisionNumber> entry : updateStarts.entrySet()) {
      myUpdateStarts.put(entry.getKey(), entry.getValue());
    }
  }

  void append(@NotNull GitRepository repository, @NotNull GitPushRepoResult result) {
    myResults.put(repository, result);
  }

  /**
   * For the specified repositories remembers the revision when update process (actually, auto-update inside the push process) has started.
   */
  void markUpdateStartIfNotMarked(@NotNull Collection<GitRepository> repositories) {
    if (myUpdateStarts.isEmpty()) {
      myBeforeUpdateLabel = LocalHistory.getInstance().putSystemLabel(myProject, "Before push");
    }
    for (GitRepository repository : repositories) {
      if (!myUpdateStarts.containsKey(repository)) {
        String currentRevision = repository.getCurrentRevision();
        if (currentRevision != null) {
          myUpdateStarts.put(repository, new GitRevisionNumber(currentRevision));
        }
      }
    }
  }

  boolean wasErrorCancelOrNotAuthorized() {
    for (GitPushRepoResult repoResult : myResults.values()) {
      if (repoResult.isOneOfErrors()) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private GroupedResult group() {
    final Map<GitRepository, GitPushRepoResult> successfulResults = new HashMap<GitRepository, GitPushRepoResult>();
    final Map<GitRepository, GitPushRepoResult> errorResults = new HashMap<GitRepository, GitPushRepoResult>();
    final Map<GitRepository, GitPushRepoResult> rejectedResults = new HashMap<GitRepository, GitPushRepoResult>();

    for (Map.Entry<GitRepository, GitPushRepoResult> entry : myResults.entrySet()) {
      GitRepository repository = entry.getKey();
      GitPushRepoResult repoResult = entry.getValue();
      switch (repoResult.getType()) {
        case SUCCESS:
          successfulResults.put(repository, repoResult);
          break;
        case ERROR:
        case CANCEL:
        case NOT_AUTHORIZED:
          errorResults.put(repository, repoResult);
          break;
        case NOT_PUSHING:
          break;
        case SOME_REJECTED:
          rejectedResults.put(repository, repoResult);
          break;
      }
    }
    return new GroupedResult(successfulResults, errorResults, rejectedResults);
  }

  boolean isEmpty() {
    return myResults.isEmpty();
  }

  @NotNull
  GitPushResult remove(@NotNull Map<GitRepository, GitBranch> repoBranchPairs) {
    GitPushResult result = new GitPushResult(myProject, myBeforeUpdateLabel, myUpdateStarts);
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

    for (Map.Entry<GitRepository, GitRevisionNumber> entry : previousResult.myUpdateStarts.entrySet()) {
      myUpdateStarts.put(entry.getKey(), entry.getValue());
    }
    myBeforeUpdateLabel = previousResult.myBeforeUpdateLabel;
  }

  @NotNull
  Map<GitRepository, GitBranch> getRejectedPushesFromCurrentBranchToTrackedBranch(GitPushInfo pushInfo) {
    final Map<GitRepository, GitBranch> rejectedPushesForCurrentBranch = new HashMap<GitRepository, GitBranch>();
    for (Map.Entry<GitRepository, GitPushRepoResult> entry : group().myRejectedResults.entrySet()) {
      GitRepository repository = entry.getKey();
      GitBranch currentBranch = repository.getCurrentBranch();
      if (currentBranch == null) {
        continue;
      }
      GitPushRepoResult repoResult = entry.getValue();
      GitPushBranchResult curBranchResult = repoResult.getBranchResults().get(currentBranch);

      if (curBranchResult == null) {
        continue;
      }

      String trackedBranchName;
      try {
        String simpleName = GitBranchUtil.getTrackedBranchName(myProject, repository.getRoot(), currentBranch.getName());
        if (simpleName == null) {
          continue;
        }
        if (simpleName.startsWith(GitBranch.REFS_HEADS_PREFIX)) {
          simpleName = simpleName.substring(GitBranch.REFS_HEADS_PREFIX.length());
        }
        String remote = GitBranchUtil.getTrackedRemoteName(myProject, repository.getRoot(), currentBranch.getName());
        if (remote == null) {
          continue;
        }
        trackedBranchName = remote + "/" + simpleName;
      }
      catch (VcsException e) {
        LOG.info("Couldn't get tracked branch for branch " + currentBranch, e);
        continue;
      }
      if (!pushInfo.getPushSpecs().get(repository).getDest().getName().equals(trackedBranchName)) {
        // push from current branch was rejected, but it was a push not to the tracked branch => ignore
        continue;
      }
      if (curBranchResult.isRejected()) {
        rejectedPushesForCurrentBranch.put(repository, currentBranch);
      }
    }
    return rejectedPushesForCurrentBranch;
  }

  /**
   * Constructs the HTML-formatted message from error outputs of failed repositories.
   * If there is only 1 repository in the project, just returns the error without writing the repository url (to avoid confusion for people
   * with only 1 root ever).
   * Otherwise adds repository URL to the error that repository produced.
   * 
   * The procedure also includes collecting for updated files (if an auto-update was performed during the push), which may be lengthy.
   */
  @NotNull
  Notification createNotification() {
    final UpdatedFiles updatedFiles = collectUpdatedFiles();

    GroupedResult groupedResult = group();
    
    boolean error = !groupedResult.myErrorResults.isEmpty();
    boolean rejected = !groupedResult.myRejectedResults.isEmpty();
    boolean success = !groupedResult.mySuccessfulResults.isEmpty();

    boolean onlyError = error && !rejected && !success;
    boolean onlyRejected = rejected && !error && !success;
    final boolean onlySuccess = success && !rejected && !error;

    int pushedCommitsNumber = calcPushedCommitTotalNumber(myResults);
    
    String title;
    NotificationType notificationType;
    if (error) {
      if (onlyError) {
        title = "Push failed";
      } else {
        title = "Push partially failed";
        if (success) {
          title += ", " + commits(pushedCommitsNumber) + " pushed";
        }
      }
      notificationType = NotificationType.ERROR;
    } else if (rejected) {
      if (onlyRejected) {
        title = "Push rejected";
      } else {
        title = "Push partially rejected, " + commits(pushedCommitsNumber) + " pushed";
      }
      notificationType = NotificationType.WARNING;
    } else {
      notificationType = NotificationType.INFORMATION;
      title = "Push successful";
    }
    
    String errorReport = reportForGroup(groupedResult.myErrorResults, GroupedResult.Type.ERROR);
    String successReport = reportForGroup(groupedResult.mySuccessfulResults, GroupedResult.Type.SUCCESS);
    String rejectedReport = reportForGroup(groupedResult.myRejectedResults, GroupedResult.Type.REJECT);

    StringBuilder sb = new StringBuilder();
    sb.append(errorReport);
    sb.append(rejectedReport);
    sb.append(successReport);
    
    if (!updatedFiles.isEmpty()) {
      sb.append("<a href='UpdatedFiles'>View files updated during the push</a>");
    }

    NotificationListener viewUpdateFilesListener = new ViewUpdatedFilesNotificationListener(updatedFiles);

    if (onlySuccess) {
      return new Notification(GitVcs.NOTIFICATION_GROUP_ID.getDisplayId(), title, sb.toString(), notificationType, viewUpdateFilesListener).setImportant(false);
    }

    return new Notification(GitVcs.IMPORTANT_ERROR_NOTIFICATION.getDisplayId(), title, sb.toString(), notificationType, viewUpdateFilesListener);
  }

  @NotNull
  private UpdatedFiles collectUpdatedFiles() {
    UpdatedFiles updatedFiles = UpdatedFiles.create();
    for (Map.Entry<GitRepository, GitRevisionNumber> updatedRepository : myUpdateStarts.entrySet()) {
      GitRepository repository = updatedRepository.getKey();
      final MergeChangeCollector collector = new MergeChangeCollector(myProject, repository.getRoot(), updatedRepository.getValue());
      final ArrayList<VcsException> exceptions = new ArrayList<VcsException>();
      collector.collect(updatedFiles, exceptions);
      for (VcsException exception : exceptions) {
        LOG.info(exception);
      }
    }
    return updatedFiles;
  }

  private static int calcPushedCommitTotalNumber(@NotNull Map<GitRepository, GitPushRepoResult> successfulResults) {
    int sum = 0;
    for (GitPushRepoResult pushRepoResult : successfulResults.values()) {
      for (GitPushBranchResult branchResult : pushRepoResult.getBranchResults().values()) {
        sum += branchResult.getNumberOfPushedCommits();
      }
    }
    return sum;
  }

  @NotNull
  private String reportForGroup(@NotNull Map<GitRepository, GitPushRepoResult> groupResult, @NotNull GroupedResult.Type resultType) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<GitRepository, GitPushRepoResult> entry : groupResult.entrySet()) {
      GitRepository repository = entry.getKey();
      GitPushRepoResult result = entry.getValue();

      if (!GitUtil.justOneGitRepository(myProject)) {
        sb.append("<code>" + repository.getPresentableUrl() + "</code>:<br/>");
      }
      if (resultType == GroupedResult.Type.SUCCESS || resultType == GroupedResult.Type.REJECT) {
        sb.append(result.getPerBranchesNonErrorReport());
      } else {
        sb.append(result.getOutput());
      }
      sb.append("<br/>");
    }
    return sb.toString();
  }


  private static class GroupedResult {
    enum Type {
      SUCCESS,
      REJECT,
      ERROR
    }
    
    private final Map<GitRepository, GitPushRepoResult> mySuccessfulResults;
    private final Map<GitRepository, GitPushRepoResult> myErrorResults;
    private final Map<GitRepository, GitPushRepoResult> myRejectedResults;

    GroupedResult(@NotNull Map<GitRepository, GitPushRepoResult> successfulResults,
                  @NotNull Map<GitRepository, GitPushRepoResult> errorResults,
                  @NotNull Map<GitRepository, GitPushRepoResult> rejectedResults) {
      mySuccessfulResults = successfulResults;
      myErrorResults = errorResults;
      myRejectedResults = rejectedResults;
    }
  }

  @NotNull
  private static String commits(int commitNum) {
    return commitNum + " " + StringUtil.pluralize("commit", commitNum);
  }

  private class ViewUpdatedFilesNotificationListener implements NotificationListener {
    private final UpdatedFiles myUpdatedFiles;

    public ViewUpdatedFilesNotificationListener(UpdatedFiles updatedFiles) {
      myUpdatedFiles = updatedFiles;
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
        if (event.getDescription().equals("UpdatedFiles")) {
          ProjectLevelVcsManagerEx vcsManager = ProjectLevelVcsManagerEx.getInstanceEx(myProject);
          UpdateInfoTree tree = vcsManager.showUpdateProjectInfo(myUpdatedFiles, "Update", ActionInfo.UPDATE, false);
          tree.setBefore(myBeforeUpdateLabel);
          tree.setAfter(LocalHistory.getInstance().putSystemLabel(myProject, "After push"));
        }
        else {
          BrowserUtil.launchBrowser(event.getDescription());
        }
      }
    }
  }
}

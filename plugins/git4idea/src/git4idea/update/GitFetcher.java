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
package git4idea.update;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import git4idea.NotificationManager;
import git4idea.commands.*;
import git4idea.config.GitVersionSpecialty;
import git4idea.jgit.GitHttpAdapter;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Kirill Likhodedov
 */
public class GitFetcher {

  private static final Logger LOG = Logger.getInstance(GitFetcher.class);

  private final Project myProject;
  private final GitRepositoryManager myRepositoryManager;
  private final ProgressIndicator myProgressIndicator;
  private final GitVcs myVcs;

  private final Collection<Exception> myErrors = new ArrayList<Exception>();

  public GitFetcher(@NotNull Project project, @NotNull ProgressIndicator progressIndicator) {
    myProject = project;
    myProgressIndicator = progressIndicator;
    myRepositoryManager = GitRepositoryManager.getInstance(project);
    myVcs = GitVcs.getInstance(project);
  }

  /**
   * Invokes 'git fetch'.
   * @return true if fetch was successful, false in the case of error.
   */
  public GitFetchResult fetch(@NotNull VirtualFile root) {
    GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    assert repository != null : "Repository can't be null for " + root + "\n" + myRepositoryManager;
    
    // TODO need to have a fair compound result here
    GitFetchResult fetchResult = GitFetchResult.success();
    for (GitRemote remote : repository.getRemotes()) {
      String url = remote.getFirstUrl();
      if (url == null) {
        continue;
      }
      if (GitHttpAdapter.shouldUseJGit(url)) {
        GitFetchResult res = GitHttpAdapter.fetch(repository, remote, url);
        res.addPruneInfo(fetchResult.getPrunedRefs());
        fetchResult = res;
        myErrors.addAll(fetchResult.getErrors());
        if (!fetchResult.isSuccess()) {
          break;
        }
      } 
      else {
        GitFetchResult res = fetchNatively(root, remote);
        res.addPruneInfo(fetchResult.getPrunedRefs());
        fetchResult = res;
        if (!fetchResult.isSuccess()) {
          break;
        }
      }
    }
    
    repository.update(GitRepository.TrackedTopic.BRANCHES);
    return fetchResult;
  }

  private GitFetchResult fetchNatively(@NotNull VirtualFile root, @NotNull GitRemote remote) {
    final GitLineHandlerPasswordRequestAware h = new GitLineHandlerPasswordRequestAware(myProject, root, GitCommand.FETCH);
    if (GitVersionSpecialty.SUPPORTS_FETCH_PRUNE.existsIn(myVcs.getVersion())) {
      h.addParameters("--prune");
    }
    h.addParameters(remote.getName());
    final GitTask fetchTask = new GitTask(myProject, h, "Fetching...");
    fetchTask.setProgressIndicator(myProgressIndicator);
    fetchTask.setProgressAnalyzer(new GitStandardProgressAnalyzer());

    GitFetchPruneDetector pruneDetector = new GitFetchPruneDetector();
    h.addLineListener(pruneDetector);

    final AtomicReference<GitFetchResult> result = new AtomicReference<GitFetchResult>();
    fetchTask.execute(true, false, new GitTaskResultHandlerAdapter() {
      @Override
      protected void onSuccess() {
        result.set(GitFetchResult.success());
      }

      @Override
      protected void onCancel() {
        LOG.info("Cancelled fetch.");
        result.set(GitFetchResult.cancel());
      }
      
      @Override
      protected void onFailure() {
        LOG.info("Error fetching: " + h.errors());
        if (!h.hadAuthRequest()) {
          myErrors.addAll(h.errors());
        } else {
          myErrors.add(new VcsException("Authentication failed"));
        }
        result.set(GitFetchResult.error(myErrors));
      }
    });

    result.get().addPruneInfo(pruneDetector.getPrunedRefs());
    return result.get();
  }

  @NotNull
  public Collection<Exception> getErrors() {
    return myErrors;
  }

  public static void displayFetchResult(@NotNull Project project,
                                        @NotNull GitFetchResult result,
                                        @Nullable String errorNotificationTitle, @NotNull Collection<? extends Exception> errors) {
    if (result.isSuccess()) {
      GitVcs.NOTIFICATION_GROUP_ID.createNotification("Fetched successfully" + result.getAdditionalInfo(), NotificationType.INFORMATION).notify(project);
    } else if (result.isCancelled()) {
      GitVcs.NOTIFICATION_GROUP_ID.createNotification("Fetch cancelled by user" + result.getAdditionalInfo(), NotificationType.WARNING).notify(project);
    } else if (result.isNotAuthorized()) {
      String title;
      String description;
      if (errorNotificationTitle != null) {
        title = errorNotificationTitle;
        description = "Fetch failed: couldn't authorize";
      } else {
        title = "Fetch failed";
        description = "Couldn't authorize";
      }
      description += result.getAdditionalInfo();
      GitUIUtil.notifyMessage(project, title, description, NotificationType.ERROR, true, null);
    } else {
      GitVcs instance = GitVcs.getInstance(project);
      if (instance != null && instance.getExecutableValidator().isExecutableValid()) {
        GitUIUtil.notifyMessage(project, "Fetch failed",  result.getAdditionalInfo(), NotificationType.ERROR, true, errors);
      }
    }
  }

  /**
   * Fetches all specified roots.
   * Once a root has failed, stops and displays the notification.
   * If needed, displays the successful notification at the end.
   * @param roots                   roots to fetch.
   * @param errorNotificationTitle  if specified, this notification title will be used instead of the standard "Fetch failed".
   *                                Use this when fetch is a part of a compound process.
   * @param notifySuccess           if set to {@code true} successful notification will be displayed.
   * @return true if all fetches were successful, false if at least one fetch failed.
   */
  public boolean fetchRootsAndNotify(@NotNull Collection<VirtualFile> roots, @Nullable String errorNotificationTitle, boolean notifySuccess) {
    Map<VirtualFile, String> additionalInfo = new HashMap<VirtualFile, String>();
    for (VirtualFile root : roots) {
      GitFetchResult result = fetch(root);
      String ai = result.getAdditionalInfo();
      if (!StringUtil.isEmptyOrSpaces(ai)) {
        additionalInfo.put(root, ai);
      }
      if (!result.isSuccess()) {
        displayFetchResult(myProject, result, errorNotificationTitle, getErrors());
        return false;
      }
    }
    if (notifySuccess) {
      GitUIUtil.notifySuccess(myProject, "", "Fetched successfully");
    }

    String addInfo = makeAdditionalInfoByRoot(additionalInfo);
    if (!StringUtil.isEmptyOrSpaces(addInfo)) {
        NotificationManager.getInstance(myProject).notify(GitVcs.MINOR_NOTIFICATION, "Fetch details", addInfo, NotificationType.INFORMATION);
    }

    return true;
  }

  @NotNull
  private String makeAdditionalInfoByRoot(@NotNull Map<VirtualFile, String> additionalInfo) {
    if (additionalInfo.isEmpty()) {
      return "";
    }
    StringBuilder info = new StringBuilder();
    if (myRepositoryManager.moreThanOneRoot()) {
      for (Map.Entry<VirtualFile, String> entry : additionalInfo.entrySet()) {
        info.append(entry.getValue()).append(" in ").append(GitUIUtil.getShortRepositoryName(myProject, entry.getKey())).append("<br/>");
      }
    }
    else {
      info.append(additionalInfo.values().iterator().next());
    }
    return info.toString();
  }

  private static class GitFetchPruneDetector extends GitLineHandlerAdapter {

    private static final Pattern PRUNE_PATTERN = Pattern.compile("\\s*x\\s*\\[deleted\\].*->\\s*(\\S*)");

    @NotNull private final Collection<String> myPrunedRefs = new ArrayList<String>();

    @Override
    public void onLineAvailable(String line, Key outputType) {
      //  x [deleted]         (none)     -> origin/frmari
      Matcher matcher = PRUNE_PATTERN.matcher(line);
      if (matcher.matches()) {
        myPrunedRefs.add(matcher.group(1));
      }
    }

    @NotNull
    public Collection<String> getPrunedRefs() {
      return myPrunedRefs;
    }
  }
}

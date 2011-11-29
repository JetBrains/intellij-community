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
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.jgit.GitHttpAdapter;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Kirill Likhodedov
 */
public class GitFetcher {

  private static final Logger LOG = Logger.getInstance(GitFetcher.class);

  private final Project myProject;
  private final GitRepositoryManager myRepositoryManager;
  private final ProgressIndicator myProgressIndicator;

  private final Collection<Exception> myErrors = new ArrayList<Exception>();

  public GitFetcher(@NotNull Project project, @NotNull ProgressIndicator progressIndicator) {
    myProject = project;
    myProgressIndicator = progressIndicator;
    myRepositoryManager = GitRepositoryManager.getInstance(project);
  }

  /**
   * Invokes 'git fetch'.
   * @return true if fetch was successful, false in the case of error.
   */
  public GitFetchResult fetch(@NotNull VirtualFile root) {
    GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    assert repository != null : "Repository can't be null for " + root + "\n" + myRepositoryManager;
    
    GitFetchResult result = GitFetchResult.success();
    for (GitRemote remote : repository.getRemotes()) {
      String url = remote.getFirstUrl();
      if (url == null) {
        continue;
      }
      if (GitHttpAdapter.isHttpUrl(url)) {
        GitFetchResult res = GitHttpAdapter.fetch(repository, remote, url);
        myErrors.addAll(res.getErrors());
        if (!res.isSuccess()) {
          result = res;
          break;
        }
      } else {
        if (!fetchNatively(root, remote)) {
          result = GitFetchResult.error(myErrors);
          break;
        }
      }
    }
    
    return result;
  }

  private boolean fetchNatively(@NotNull VirtualFile root, @NotNull GitRemote remote) {
    final GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.FETCH);
    h.addParameters(remote.getName());
    final GitTask fetchTask = new GitTask(myProject, h, "Fetching...");
    fetchTask.setProgressIndicator(myProgressIndicator);
    fetchTask.setProgressAnalyzer(new GitStandardProgressAnalyzer());
    final AtomicBoolean success = new AtomicBoolean();
    fetchTask.execute(true, false, new GitTaskResultHandlerAdapter() {
      @Override
      protected void onSuccess() {
        success.set(true);
      }

      @Override
      protected void onCancel() {
        LOG.info("Cancelled fetch.");
      }

      @Override
      protected void onFailure() {
        LOG.info("Error fetching: " + h.errors());
        myErrors.addAll(h.errors());
      }
    });
    return success.get();
  }

  @NotNull
  public Collection<Exception> getErrors() {
    return myErrors;
  }

  public static void displayFetchResult(@NotNull Project project,
                                        @NotNull GitFetchResult result,
                                        @Nullable String errorNotificationTitle, @NotNull Collection<? extends Exception> errors) {
    if (result.isSuccess()) {
      GitVcs.NOTIFICATION_GROUP_ID.createNotification("Fetched successfully", NotificationType.WARNING).notify(project);
    } else if (result.isCancelled()) {
      GitVcs.NOTIFICATION_GROUP_ID.createNotification("Fetch cancelled by user", NotificationType.WARNING).notify(project);
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
      GitUIUtil.notifyMessage(project, title, description, NotificationType.ERROR, true, null);
    } else {
      GitVcs instance = GitVcs.getInstance(project);
      if (instance != null && instance.getExecutableValidator().isExecutableValid()) {
        GitUIUtil.notifyMessage(project, "Fetch failed", null, NotificationType.ERROR, true, errors);
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
    for (VirtualFile root : roots) {
      GitFetchResult result = fetch(root);
      if (!result.isSuccess()) {
        displayFetchResult(myProject, result, errorNotificationTitle, getErrors());
        return false;
      }
    }
    if (notifySuccess) {
      GitUIUtil.notifySuccess(myProject, "", "Fetched successfully");
    }
    return true;
  }
}

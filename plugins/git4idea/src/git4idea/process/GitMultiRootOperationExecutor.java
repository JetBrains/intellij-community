/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.process;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import git4idea.GitVcs;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Kirill Likhodedov
 */
class GitMultiRootOperationExecutor {

  private static final Logger LOG = Logger.getInstance(GitMultiRootOperationExecutor.class);

  @NotNull private final Project myProject;
  private final Collection<GitRepository> myRepositories;
  private final Collection<GitRepository> mySuccessfulRepositories = new ArrayList<GitRepository>();

  GitMultiRootOperationExecutor(@NotNull Project project, @NotNull Collection<GitRepository> repositories) {
    myProject = project;
    myRepositories = repositories;
  }

  void execute(@NotNull GitBranchOperation operation) {
    GitRepository[] repositories = ArrayUtil.toObjectArray(myRepositories, GitRepository.class);
    String errorTitle = null;
    String errorDescription = null;
    boolean retried = false;

    for (int i = 0; i < myRepositories.size(); i++) {  // iterating via index, because we'll need to retry an iteration
      GitRepository repository = repositories[i];

      GitBranchOperationResult result = operation.execute(repository);
      if (result.isSuccess()) {
        mySuccessfulRepositories.add(repository);
      }
      else if (result.isResolvable() && !retried) {
        GitBranchOperationResult resolveResult = operation.tryResolve();
        if (resolveResult.isSuccess()) {
          i--; // retry
          retried = true;
          continue;
        }
        else { // didn't merge
          errorTitle = resolveResult.getErrorTitle();
          errorDescription = resolveResult.getErrorDescription();
          break;
        }
      }
      else { // other unrecoverable error
        errorTitle = result.getErrorTitle();
        errorDescription = result.getErrorDescription();
        break;
      }
    }

    if (errorTitle != null) {
      if (mySuccessfulRepositories.isEmpty()) {
        if (!operation.showFatalError()) {
          assert errorDescription != null : "errorDescription can't be null with errorTitle=" + errorTitle;
          showFatalError(errorTitle, errorDescription, myProject);
        }
      }
      else {
        if (operation.rollbackable()) {
          boolean choseToRollback = proposeRollback(errorTitle, errorDescription);
          if (choseToRollback) {
            operation.rollback(mySuccessfulRepositories);
          }
        }
      }
    }
    else {
      // total success
      notifySuccess(operation.getSuccessMessage(), myProject);
    }

    updateRepositories();
  }

  private boolean proposeRollback(final String title, final String message) {
    final AtomicBoolean ok = new AtomicBoolean();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        StringBuilder fullmsg = new StringBuilder(message);
        fullmsg.append("\n\nThe action however succeeded for the following repositories: \n").append(joinRepositoryUrls(mySuccessfulRepositories, "\n"))
          .append("\nYou may rollback the action on them, otherwise branches will diverge");
        ok.set(Messages.OK == Messages.showYesNoDialog(myProject, fullmsg.toString(), title, "Rollback", "Don't rollback", Messages.getErrorIcon()));
      }
    });
    return ok.get();
  }

  static void showFatalError(@NotNull String title, @NotNull String message, @NotNull Project project) {
    GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification(title, message, NotificationType.ERROR, null).notify(project);
  }

  private void updateRepositories() {
    GitRepositoryManager.getInstance(myProject).updateAllRepositories(GitRepository.TrackedTopic.CURRENT_BRANCH,
                                                                      GitRepository.TrackedTopic.BRANCHES);
  }

  static void notifySuccess(@NotNull String message, @NotNull Project project) {
    GitVcs.NOTIFICATION_GROUP_ID.createNotification(message, NotificationType.INFORMATION).notify(project);
  }

  @NotNull
  public Collection<GitRepository> getSuccessfulRepositories() {
    return mySuccessfulRepositories;
  }

  @NotNull
  static String joinRepositoryUrls(@NotNull Collection<GitRepository> repositories, @NotNull String separator) {
    return StringUtil.join(repositories, new Function<GitRepository, String>() {
      @Override
      public String fun(GitRepository repository) {
        return repository.getPresentableUrl();
      }
    }, separator);
  }
}

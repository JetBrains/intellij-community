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
package git4idea.branch;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import git4idea.GitVcs;
import git4idea.MessageManager;
import git4idea.NotificationManager;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.util.GitUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Common class for Git operations with branches aware of multi-root configuration,
 * which means showing combined error information, proposing to rollback, etc.
 *
 * @author Kirill Likhodedov
 */
abstract class GitBranchOperation {

  static final String UNMERGED_FILES_ERROR_TITLE = "Can't checkout because of unmerged files";
  static final String UNMERGED_FILES_ERROR_NOTIFICATION_DESCRIPTION =
    "You have to <a href='resolve'>resolve</a> all merge conflicts before checkout.<br/>" +
    "After resolving conflicts you also probably would want to commit your files to the current branch.";

  @NotNull protected final Project myProject;
  @NotNull private final Collection<GitRepository> myRepositories;
  @NotNull private final ProgressIndicator myIndicator;

  @NotNull private final Collection<GitRepository> mySuccessfulRepositories;
  @NotNull private final Collection<GitRepository> myRemainingRepositories;

  protected GitBranchOperation(@NotNull Project project, @NotNull Collection<GitRepository> repositories,
                               @NotNull ProgressIndicator indicator) {
    myProject = project;
    myRepositories = repositories;
    myIndicator = indicator;
    mySuccessfulRepositories = new ArrayList<GitRepository>();
    myRemainingRepositories = new ArrayList<GitRepository>(myRepositories);
  }

  protected abstract void execute();

  protected abstract void rollback();

  @NotNull
  public abstract String getSuccessMessage();

  @NotNull
  protected abstract String getRollbackProposal();

  /**
   * @return next repository that wasn't handled (e.g. checked out) yet.
   */
  @NotNull
  protected GitRepository next() {
    return myRemainingRepositories.iterator().next();
  }

  /**
   * @return true if there are more repositories on which the operation wasn't executed yet.
   */
  protected boolean hasMoreRepositories() {
    return !myRemainingRepositories.isEmpty();
  }

  /**
   * Marks repositories as successful, i.e. they won't be handled again.
   */
  protected void markSuccessful(GitRepository... repositories) {
    for (GitRepository repository : repositories) {
      mySuccessfulRepositories.add(repository);
      myRemainingRepositories.remove(repository);
    }
  }

  /**
   * @return true if the operation has already succeeded in at least one of repositories.
   */
  protected boolean wereSuccessful() {
    return !mySuccessfulRepositories.isEmpty();
  }
  
  @NotNull
  protected Collection<GitRepository> getSuccessfulRepositories() {
    return mySuccessfulRepositories;
  }
  
  @NotNull
  protected String successfulRepositoriesJoined() {
    return StringUtil.join(mySuccessfulRepositories, new Function<GitRepository, String>() {
      @Override
      public String fun(GitRepository repository) {
        return repository.getPresentableUrl();
      }
    }, "<br/>");
  }
  
  @NotNull
  protected Collection<GitRepository> getRepositories() {
    return myRepositories;
  }

  @NotNull
  protected Collection<GitRepository> getRemainingRepositories() {
    return myRemainingRepositories;
  }

  protected void notifySuccess() {
    NotificationManager.getInstance(myProject).notify(GitVcs.NOTIFICATION_GROUP_ID, "", getSuccessMessage(), NotificationType.INFORMATION);
  }

  /**
   * Show fatal error as a notification or as a dialog with rollback proposal.
   */
  protected void fatalError(@NotNull String title, @NotNull String message) {
    if (wereSuccessful())  {
      showFatalErrorDialogWithRollback(title, message);
    }
    else {
      showFatalNotification(title, message);
    }
  }

  protected void showFatalErrorDialogWithRollback(@NotNull final String title, @NotNull final String message) {
    final AtomicBoolean ok = new AtomicBoolean();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        String description = message + getRollbackProposal();
        ok.set(Messages.OK ==
               MessageManager.showYesNoDialog(myProject, description, title, "Rollback", "Don't rollback", Messages.getErrorIcon()));
      }
    });
    if (ok.get()) {
      rollback();
    }
  }

  protected void showFatalNotification(@NotNull String title, @NotNull String message) {
    notifyError(title, message);
  }

  protected void notifyError(@NotNull String title, @NotNull String message) {
    NotificationManager.getInstance(myProject).notify(GitVcs.IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.ERROR);
  }

  @NotNull
  protected ProgressIndicator getIndicator() {
    return myIndicator;
  }

  /**
   * Display the error saying that the operation can't be performed because there are unmerged files in a repository.
   * Such error prevents checking out and creating new branch.
   */
  protected void fatalUnmergedFilesError() {
    if (wereSuccessful()) {
      showUnmergedFilesDialogWithRollback();
    }
    else {
      showUnmergedFilesNotification();
    }
  }

  private void showUnmergedFilesDialogWithRollback() {
    final AtomicBoolean ok = new AtomicBoolean();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override public void run() {
        String description = "You have to resolve all merge conflicts before checkout.<br/>" + getRollbackProposal();
        // suppressing: this message looks ugly if capitalized by words
        //noinspection DialogTitleCapitalization
        ok.set(Messages.OK == MessageManager.showYesNoDialog(myProject, description, UNMERGED_FILES_ERROR_TITLE, "Rollback", "Don't rollback", Messages.getErrorIcon()));
      }
    });
    if (ok.get()) {
      rollback();
    }
  }

  private void showUnmergedFilesNotification() {
    String title = UNMERGED_FILES_ERROR_TITLE;
    String description = UNMERGED_FILES_ERROR_NOTIFICATION_DESCRIPTION;
    NotificationManager.getInstance(myProject).notify(GitVcs.IMPORTANT_ERROR_NOTIFICATION, title, description, NotificationType.ERROR, new NotificationListener() {
      @Override public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().equals("resolve")) {
          GitConflictResolver.Params params = new GitConflictResolver.Params().
                setMergeDescription("The following files have unresolved conflicts. You need to resolve them before checking out.").
                setErrorNotificationTitle("Unresolved files remain.");
          new GitConflictResolver(myProject, GitUtil.getRoots(getRepositories()), params).merge();
        }
      }
    });
  }

}

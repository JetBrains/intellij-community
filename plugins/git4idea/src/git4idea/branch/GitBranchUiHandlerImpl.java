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
package git4idea.branch;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xml.util.XmlStringUtil;
import git4idea.DialogManager;
import git4idea.GitCommit;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.ui.ChangesBrowserWithRollback;
import git4idea.util.GitSimplePathsBrowser;
import git4idea.util.GitUntrackedFilesHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.ui.Messages.YES;
import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static git4idea.branch.GitBranchUiHandler.DeleteRemoteBranchDecision.CANCEL;
import static git4idea.branch.GitBranchUiHandler.DeleteRemoteBranchDecision.DELETE;

public class GitBranchUiHandlerImpl implements GitBranchUiHandler {

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final ProgressIndicator myProgressIndicator;

  public GitBranchUiHandlerImpl(@NotNull Project project, @NotNull Git git, @NotNull ProgressIndicator indicator) {
    myProject = project;
    myGit = git;
    myProgressIndicator = indicator;
  }

  @Override
  public boolean notifyErrorWithRollbackProposal(@NotNull final String title, @NotNull final String message,
                                                 @NotNull final String rollbackProposal) {
    final AtomicBoolean ok = new AtomicBoolean();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      StringBuilder description = new StringBuilder();
      if (!StringUtil.isEmptyOrSpaces(message)) {
        description.append(message).append("<br/>");
      }
      description.append(rollbackProposal);
      ok.set(YES == DialogManager.showOkCancelDialog(myProject, XmlStringUtil.wrapInHtml(description), title,
                                                     "Rollback", "Don't rollback", Messages.getErrorIcon()));
    });
    return ok.get();
  }

  @Override
  public void showUnmergedFilesNotification(@NotNull final String operationName, @NotNull final Collection<GitRepository> repositories) {
    String title = unmergedFilesErrorTitle(operationName);
    String description = unmergedFilesErrorNotificationDescription(operationName);
    VcsNotifier.getInstance(myProject).notifyError(title, description,
      new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification,
                                    @NotNull HyperlinkEvent event) {
          if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().equals("resolve")) {
            GitConflictResolver.Params params = new GitConflictResolver.Params().
              setMergeDescription(String.format("The following files have unresolved conflicts. You need to resolve them before %s.",
                                                operationName)).
              setErrorNotificationTitle("Unresolved files remain.");
            new GitConflictResolver(myProject, myGit, GitUtil.getRootsFromRepositories(repositories), params).merge();
          }
        }
      }
    );
  }

  @Override
  public boolean showUnmergedFilesMessageWithRollback(@NotNull final String operationName, @NotNull final String rollbackProposal) {
    final AtomicBoolean ok = new AtomicBoolean();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      String description = String.format("<html>You have to resolve all merge conflicts before %s.<br/>%s</html>",
                                         operationName, rollbackProposal);
      // suppressing: this message looks ugly if capitalized by words
      //noinspection DialogTitleCapitalization
      ok.set(YES == DialogManager.showOkCancelDialog(myProject, description, unmergedFilesErrorTitle(operationName),
                                                     "Rollback", "Don't rollback", Messages.getErrorIcon()));
    });
    return ok.get();
  }

  @Override
  public void showUntrackedFilesNotification(@NotNull String operationName, @NotNull VirtualFile root, @NotNull Collection<String> relativePaths) {
    GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(myProject, root, relativePaths, operationName, null);
  }

  @Override
  public boolean showUntrackedFilesDialogWithRollback(@NotNull String operationName, @NotNull final String rollbackProposal,
                                                      @NotNull VirtualFile root, @NotNull final Collection<String> relativePaths) {
    return GitUntrackedFilesHelper.showUntrackedFilesDialogWithRollback(myProject, operationName, rollbackProposal, root, relativePaths);
  }

  @NotNull
  @Override
  public ProgressIndicator getProgressIndicator() {
    return myProgressIndicator;
  }

  @NotNull
  @Override
  public GitSmartOperationDialog.Choice showSmartOperationDialog(@NotNull Project project,
                                                                 @NotNull List<Change> changes,
                                                                 @NotNull Collection<String> paths,
                                                                 @NotNull String operation,
                                                                 @Nullable String forceButtonTitle) {
    JComponent fileBrowser;
    if (!changes.isEmpty()) {
      fileBrowser = new ChangesBrowserWithRollback(project, changes);
    }
    else {
      fileBrowser = new GitSimplePathsBrowser(project, paths);
    }
    return GitSmartOperationDialog.showAndGetAnswer(myProject, fileBrowser, operation, forceButtonTitle);
  }

  @Override
  public boolean showBranchIsNotFullyMergedDialog(@NotNull Project project,
                                                  @NotNull Map<GitRepository, List<GitCommit>> history,
                                                  @NotNull Map<GitRepository, String> baseBranches,
                                                  @NotNull String removedBranch) {
    AtomicBoolean restore = new AtomicBoolean();
    ApplicationManager.getApplication().invokeAndWait(() -> restore.set(
      GitBranchIsNotFullyMergedDialog.showAndGetAnswer(myProject, history, baseBranches, removedBranch)));
    return restore.get();
  }

  @NotNull
  @Override
  public DeleteRemoteBranchDecision confirmRemoteBranchDeletion(@NotNull String branchName,
                                                                @NotNull Collection<String> trackingBranches,
                                                                @NotNull Collection<GitRepository> repositories) {
    String title = "Delete Remote Branch";
    String message = "Delete remote branch " + branchName + "?";

    if (trackingBranches.isEmpty()) {
      return YES == DialogManager.showOkCancelDialog(myProject, message, title, "Delete", "Cancel", getQuestionIcon()) ? DELETE : CANCEL;
    }

    String checkboxMessage = trackingBranches.size() == 1
                             ? "Delete tracking local branch " + trackingBranches.iterator().next() + " as well"
                             : "Delete tracking local branches " + StringUtil.join(trackingBranches, ", ");

      Ref<Boolean> deleteChoice = Ref.create(false);
      boolean delete = MessageDialogBuilder.yesNo(title, message).project(myProject).yesText("Delete").noText("Cancel").doNotAsk(
        new DialogWrapper.DoNotAskOption.Adapter() {
          @Override
          public void rememberChoice(boolean isSelected, int exitCode) {
            deleteChoice.set(isSelected);
          }

          @NotNull
          @Override
          public String getDoNotShowMessage() {
            return checkboxMessage;
          }
        }).show() == YES;
      boolean deleteTracking = deleteChoice.get();
      return delete
             ? deleteTracking ? DeleteRemoteBranchDecision.DELETE_WITH_TRACKING : DELETE
             : CANCEL;
  }

  @NotNull
  private static String unmergedFilesErrorTitle(@NotNull String operationName) {
    return "Can't " + operationName + " because of unmerged files";
  }

  @NotNull
  private static String unmergedFilesErrorNotificationDescription(String operationName) {
    return "You have to <a href='resolve'>resolve</a> all merge conflicts before " + operationName + ".<br/>" +
           "After resolving conflicts you also probably would want to commit your files to the current branch.";
  }
}

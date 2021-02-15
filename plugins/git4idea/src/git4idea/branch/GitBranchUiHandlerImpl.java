// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import git4idea.DialogManager;
import git4idea.GitCommit;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.util.GitUntrackedFilesHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.CommonBundle.getCancelButtonText;
import static com.intellij.CommonBundle.message;
import static com.intellij.openapi.ui.Messages.YES;
import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static git4idea.GitNotificationIdsHolder.UNRESOLVED_CONFLICTS;
import static git4idea.branch.GitBranchUiHandler.DeleteRemoteBranchDecision.CANCEL;
import static git4idea.branch.GitBranchUiHandler.DeleteRemoteBranchDecision.DELETE;

public class GitBranchUiHandlerImpl implements GitBranchUiHandler {
  @NonNls private static final String RESOLVE_HREF_ATTRIBUTE = "resolve";

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final ProgressIndicator myProgressIndicator;

  public GitBranchUiHandlerImpl(@NotNull Project project, @NotNull Git git, @NotNull ProgressIndicator indicator) {
    myProject = project;
    myGit = git;
    myProgressIndicator = indicator;
  }

  @Override
  public boolean notifyErrorWithRollbackProposal(@NotNull final String title,
                                                 @NotNull final String message,
                                                 @NotNull final String rollbackProposal) {
    final AtomicBoolean ok = new AtomicBoolean();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      StringBuilder description = new StringBuilder();
      if (!StringUtil.isEmptyOrSpaces(message)) {
        description.append(message).append(UIUtil.BR);
      }
      description.append(rollbackProposal);
      ok.set(YES == DialogManager.showOkCancelDialog(myProject, XmlStringUtil.wrapInHtml(description), title,
                                                     GitBundle.message("branch.ui.handler.rollback"),
                                                     GitBundle.message("branch.ui.handler.do.not.rollback"), Messages.getErrorIcon()));
    });
    return ok.get();
  }

  @Override
  public void showUnmergedFilesNotification(@NotNull final String operationName, @NotNull final Collection<? extends GitRepository> repositories) {
    String title = unmergedFilesErrorTitle(operationName);
    String description = unmergedFilesErrorNotificationDescription(operationName);
    VcsNotifier.getInstance(myProject).notifyError(UNRESOLVED_CONFLICTS, title, description,
                                                   new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification,
                                    @NotNull HyperlinkEvent event) {
          if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().equals(RESOLVE_HREF_ATTRIBUTE)) {
            GitConflictResolver.Params params = new GitConflictResolver.Params(myProject).
              setMergeDescription(GitBundle.message("branch.ui.handler.merge.notification.description", operationName)).
              setErrorNotificationTitle(GitBundle.message("branch.ui.handler.merge.error.notification.title"));
            new GitConflictResolver(myProject, GitUtil.getRootsFromRepositories(repositories), params).merge();
          }
        }
      }
    );
  }

  @Override
  public boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull final String rollbackProposal) {
    final AtomicBoolean ok = new AtomicBoolean();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      String description = XmlStringUtil.wrapInHtml(
        GitBundle.message("branch.ui.handler.you.have.to.resolve.all.conflicts.before.operation.name", operationName, rollbackProposal));
      // suppressing: this message looks ugly if capitalized by words
      ok.set(YES == DialogManager.showOkCancelDialog(myProject, description, unmergedFilesErrorTitle(operationName),
                                                     GitBundle.message("branch.ui.handler.rollback"),
                                                     GitBundle.message("branch.ui.handler.do.not.rollback"), Messages.getErrorIcon()));
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

  @Override
  public GitSmartOperationDialog.Choice showSmartOperationDialog(@NotNull Project project,
                                                                 @NotNull List<? extends Change> changes,
                                                                 @NotNull Collection<String> paths,
                                                                 @NotNull String operation,
                                                                 @Nullable String forceButtonTitle) {
    Ref<GitSmartOperationDialog.Choice> exitCode = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(
      () -> exitCode.set(GitSmartOperationDialog.show(project, changes, paths, StringUtil.capitalize(operation), forceButtonTitle)));
    return exitCode.get();
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
  public DeleteRemoteBranchDecision confirmRemoteBranchDeletion(@NotNull List<String> branchNames,
                                                                @NotNull Collection<@NlsSafe String> trackingBranches,
                                                                @NotNull Collection<GitRepository> repositories) {
    boolean deleteMultipleBranches = branchNames.size() > 1;
    String title = GitBundle.message("branch.ui.handler.delete.remote.branches", branchNames.size());
    String remoteBranches = deleteMultipleBranches ? StringUtil.join(branchNames, ", ") : branchNames.iterator().next();
    String message = GitBundle.message("branch.ui.handler.delete.remote.branches.question", branchNames.size(), remoteBranches);
    String deleteButtonText = deleteMultipleBranches ? GitBundle.message("branch.ui.handler.delete.all") : message("button.delete");

    if (trackingBranches.isEmpty()) {
      return YES ==
             DialogManager
               .showOkCancelDialog(myProject, message, title, deleteButtonText, getCancelButtonText(), getQuestionIcon()) ? DELETE : CANCEL;
    }
    String forBranch = GitBundle.message("branch.ui.handler.delete.tracking.local.branch.as.well", trackingBranches.iterator().next());
    String forBranches = new HtmlBuilder().append(GitBundle.message("branch.ui.handler.delete.tracking.local.branches")).br()
      .appendWithSeparators(HtmlChunk.raw(", " + HtmlChunk.br()), ContainerUtil.map(trackingBranches, it -> HtmlChunk.text(it)))
      .wrapWith(HtmlChunk.html()).toString();
    String checkboxMessage = trackingBranches.size() == 1 ? forBranch : forBranches;

    Ref<Boolean> deleteChoice = Ref.create(false);
    boolean delete =
      MessageDialogBuilder.yesNo(title, message).yesText(deleteButtonText).noText(getCancelButtonText())
        .doNotAsk(new DialogWrapper.DoNotAskOption.Adapter() {
          @Override
          public void rememberChoice(boolean isSelected, int exitCode) {
            deleteChoice.set(isSelected);
          }

          @NotNull
          @Override
          public String getDoNotShowMessage() {
            return checkboxMessage;
          }
        })
        .ask(myProject);
      boolean deleteTracking = deleteChoice.get();
      return delete
             ? deleteTracking ? DeleteRemoteBranchDecision.DELETE_WITH_TRACKING : DELETE
             : CANCEL;
  }

  @NotNull
  @NlsContexts.DialogTitle
  private static String unmergedFilesErrorTitle(@NotNull String operationName) {
    return GitBundle.message("branch.ui.handler.can.not.operation.name.because.of.unmerged.files", operationName);
  }

  @NotNull
  @NlsContexts.NotificationContent
  private static String unmergedFilesErrorNotificationDescription(String operationName) {
    return GitBundle.message("branch.ui.handler.unmerged.files.error.notification", RESOLVE_HREF_ATTRIBUTE, operationName);
  }
}

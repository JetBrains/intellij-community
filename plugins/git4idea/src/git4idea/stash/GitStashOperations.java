// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.StashInfo;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import static com.intellij.xml.util.XmlStringUtil.wrapInHtml;
import static com.intellij.xml.util.XmlStringUtil.wrapInHtmlTag;

public class GitStashOperations {
  public static boolean dropStashWithConfirmation(@NotNull Project project,
                                                  @NotNull VirtualFile root,
                                                  @NotNull Component parentComponent,
                                                  @NotNull StashInfo stash) {
    if (Messages.YES == Messages.showYesNoDialog(parentComponent,
                                                 GitBundle
                                                   .message("git.unstash.drop.confirmation.message", stash.getStash(), stash.getMessage()),
                                                 GitBundle.message("git.unstash.drop.confirmation.title", stash.getStash()),
                                                 Messages.getQuestionIcon())) {
      final ModalityState current = ModalityState.current();
      ProgressManager.getInstance().run(new Task.Modal(
        project,
        GitBundle.message("unstash.dialog.remove.stash.progress.indicator.title", stash.getStash()),
        true
      ) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          GitLineHandler h = new GitLineHandler(project, root, GitCommand.STASH);
          h.addParameters("drop", stash.getStash());
          try {
            Git.getInstance().runCommand(h).throwOnError();
          }
          catch (final VcsException ex) {
            ApplicationManager.getApplication()
              .invokeLater(() -> GitUIUtil.showOperationError(project, ex, h.printableCommandLine()), current);
          }
        }
      });
      return true;
    }
    return false;
  }

  public static boolean clearStashesWithConfirmation(@NotNull Project project,
                                                     @NotNull VirtualFile root,
                                                     @NotNull Component parentComponent) {
    if (Messages.YES == Messages.showYesNoDialog(parentComponent,
                                                 GitBundle.message("git.unstash.clear.confirmation.message"),
                                                 GitBundle.message("git.unstash.clear.confirmation.title"), Messages.getWarningIcon())) {
      GitLineHandler h = new GitLineHandler(project, root, GitCommand.STASH);
      h.addParameters("clear");

      new Task.Modal(project, GitBundle.message("unstash.clearing.stashes"), false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          GitCommandResult result = Git.getInstance().runCommand(h);
          if (!result.success()) {
            ApplicationManager.getApplication().invokeLater(() ->
                                                              GitUIUtil.showOperationError(project,
                                                                                           GitBundle.message("unstash.clearing.stashes"),
                                                                                           result.getErrorOutputAsJoinedString()));
          }
        }
      }.queue();
      return true;
    }
    return false;
  }

  public static void viewStash(@NotNull Project project, @NotNull VirtualFile root, @NotNull StashInfo stash) {
    String selectedStash = stash.getStash();
    try {
      String hash = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        () -> resolveHashOfStash(project, root, selectedStash),
        GitBundle.message("unstash.dialog.stash.details.load.progress.indicator.title"),
        true,
        project
      );
      GitUtil.showSubmittedFiles(project, hash, root, true, false);
    }
    catch (VcsException ex) {
      GitUIUtil.showOperationError(project, ex, GitBundle.message("operation.name.resolving.revision"));
    }
  }

  private static @NotNull String resolveHashOfStash(@NotNull Project project, @NotNull VirtualFile root, @NotNull String stash)
    throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.REV_LIST);
    h.setSilent(true);
    h.addParameters("--timestamp", "--max-count=1", stash);
    h.endOptions();
    String output = Git.getInstance().runCommand(h).getOutputOrThrow();
    return GitRevisionNumber.parseRevlistOutputAsRevisionNumber(h, output).asString();
  }

  public static boolean unstash(@NotNull Project project, @NotNull VirtualFile root, @NotNull StashInfo stash,
                                @Nullable String branch, boolean popStash, boolean reinstateIndex) {
    GitLineHandler h = unstashHandler(project, root, stash, branch, popStash, reinstateIndex);
    boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      //better to use quick to keep consistent state with ui
      GitRepository repository = Objects.requireNonNull(GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root));
      Hash hash = Git.getInstance().resolveReference(repository, stash.getStash());
      GitStashUtils.unstash(project, Collections.singletonMap(root, hash), r -> h,
                            new UnstashConflictResolver(project, root, stash));
    }, GitBundle.message("unstash.unstashing"), true, project);

    if (completed) {
      VcsNotifier.getInstance(project).notifySuccess("git.unstash.patch.applied",
                                                     "", VcsBundle.message("patch.apply.success.applied.text"));
      return true;
    }
    return false;
  }

  private static @NotNull GitLineHandler unstashHandler(@NotNull Project project, @NotNull VirtualFile root, @NotNull StashInfo stash,
                                                        @Nullable String branch, boolean popStash, boolean reinstateIndex) {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.STASH);
    if (branch != null && branch.length() == 0) {
      h.addParameters(popStash ? "pop" : "apply");
      if (reinstateIndex) {
        h.addParameters("--index");
      }
    }
    else {
      h.addParameters("branch", branch);
    }
    h.addParameters(stash.getStash());
    return h;
  }

  private static class UnstashConflictResolver extends GitConflictResolver {
    private final VirtualFile myRoot;
    private final StashInfo myStashInfo;

    UnstashConflictResolver(Project project, VirtualFile root, StashInfo stashInfo) {
      super(project, Collections.singleton(root), makeParams(project, stashInfo));
      myRoot = root;
      myStashInfo = stashInfo;
    }

    private static Params makeParams(Project project, StashInfo stashInfo) {
      Params params = new Params(project);
      params.setErrorNotificationTitle(GitBundle.message("unstash.unstashed.with.conflicts.error.title"));
      params.setMergeDialogCustomizer(new UnstashMergeDialogCustomizer(stashInfo));
      return params;
    }

    @Override
    protected void notifyUnresolvedRemain() {
      VcsNotifier.getInstance(myProject).notifyImportantWarning("git.unstash.with.unresolved.conflicts",
                                                                GitBundle.message("unstash.dialog.unresolved.conflict.warning.notification.title"),
                                                                GitBundle.message("unstash.dialog.unresolved.conflict.warning.notification.message"),
                                                                new NotificationListener() {
                                                                  @Override
                                                                  public void hyperlinkUpdate(@NotNull Notification notification,
                                                                                              @NotNull HyperlinkEvent event) {
                                                                    if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                                                      if (event.getDescription().equals("resolve")) {
                                                                        new UnstashConflictResolver(myProject, myRoot, myStashInfo).mergeNoProceed();
                                                                      }
                                                                    }
                                                                  }
                                                                }
      );
    }
  }

  private static class UnstashMergeDialogCustomizer extends MergeDialogCustomizer {
    private final StashInfo myStashInfo;

    UnstashMergeDialogCustomizer(StashInfo stashInfo) {
      myStashInfo = stashInfo;
    }

    @NotNull
    @Override
    public String getMultipleFileMergeDescription(@NotNull Collection<VirtualFile> files) {
      return wrapInHtml(
        GitBundle.message(
          "unstash.conflict.dialog.description.label.text",
          wrapInHtmlTag(myStashInfo.getStash() + "\"" + myStashInfo.getMessage() + "\"", "code")
        )
      );
    }

    @NotNull
    @Override
    public String getLeftPanelTitle(@NotNull VirtualFile file) {
      return GitBundle.message("unstash.conflict.diff.dialog.left.title");
    }

    @NotNull
    @Override
    public String getRightPanelTitle(@NotNull VirtualFile file, VcsRevisionNumber revisionNumber) {
      return GitBundle.message("unstash.conflict.diff.dialog.right.title");
    }
  }
}

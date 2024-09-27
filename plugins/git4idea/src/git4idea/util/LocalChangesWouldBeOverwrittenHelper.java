// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util;

import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.i18n.GitBundle;
import git4idea.ui.ChangesBrowserWithRollback;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public final class LocalChangesWouldBeOverwrittenHelper {
  public static void showErrorNotification(final @NotNull Project project,
                                           @NonNls @NotNull String displayId,
                                           final @NotNull VirtualFile root,
                                           final @NotNull String operationName,
                                           final @NotNull Collection<String> relativeFilePaths) {
    final Collection<String> absolutePaths = GitUtil.toAbsolute(root, relativeFilePaths);
    final List<Change> changes = GitUtil.findLocalChangesForPaths(project, root, absolutePaths, false);

    VcsNotifier.importantNotification()
      .createNotification(GitBundle.message("notification.title.git.operation.failed", StringUtil.capitalize(operationName)),
                          GitBundle.message(getOverwrittenByMergeMessage()),
                          NotificationType.ERROR)
      .setDisplayId(displayId)
      .addAction(NotificationAction.createSimple(
        GitBundle.messagePointer("local.changes.would.be.overwritten.by.merge.view.them.action"), () -> {
          showErrorDialog(project, operationName, changes, absolutePaths);
        }))
      .notify(project);
  }

  private static void showErrorDialog(@NotNull Project project, @NotNull String operationName, @NotNull List<? extends Change> changes,
                                      @NotNull Collection<String> absolutePaths) {
    String title = GitBundle.message("dialog.title.local.changes.prevent.from.operation", StringUtil.capitalize(operationName));
    String description = GitBundle.message(getOverwrittenByMergeMessage());
    if (changes.isEmpty()) {
      GitUtil.showPathsInDialog(project, absolutePaths, title, description);
    }
    else {
      ChangesBrowserWithRollback changesViewer = new ChangesBrowserWithRollback(project, changes);

      DialogBuilder builder = new DialogBuilder(project);
      builder.setNorthPanel(new MultiLineLabel(description));
      builder.setCenterPanel(changesViewer);
      builder.addDisposable(changesViewer);
      builder.addOkAction();
      builder.setTitle(title);
      builder.show();
    }
  }

  @ApiStatus.Internal
  public static @Nls @NotNull String getOverwrittenByMergeMessage() {
    String mergeOperation = GitBundle.message("merge.operation.name");
    String stashOperation = StringUtil.toLowerCase(GitBundle.message("local.changes.save.policy.stash"));
    return GitBundle.message("warning.your.local.changes.would.be.overwritten.by", mergeOperation, stashOperation);
  }
}

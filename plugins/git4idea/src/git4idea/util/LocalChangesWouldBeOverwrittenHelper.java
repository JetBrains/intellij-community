// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.GitUtil;
import git4idea.i18n.GitBundle;
import git4idea.ui.ChangesBrowserWithRollback;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.List;

public final class LocalChangesWouldBeOverwrittenHelper {

  private static @Nls @NotNull String getErrorDescription(boolean forNotification) {
    String message = GitBundle.message("warning.your.local.changes.would.be.overwritten.by.merge");
    if (forNotification) {
      return new HtmlBuilder()
        .appendRaw(StringUtil.replace(message, "\n", UIUtil.BR))
        .appendLink("view", GitBundle.message("link.label.local.changes.would.be.overwritten.by.merge.view.them"))
        .toString();
    }
    else {
      return message;
    }
  }

  public static void showErrorNotification(final @NotNull Project project,
                                           @NonNls @Nullable String displayId,
                                           final @NotNull VirtualFile root,
                                           final @NotNull String operationName,
                                           final @NotNull Collection<String> relativeFilePaths) {
    final Collection<String> absolutePaths = GitUtil.toAbsolute(root, relativeFilePaths);
    final List<Change> changes = GitUtil.findLocalChangesForPaths(project, root, absolutePaths, false);
    String notificationTitle = GitBundle.message("notification.title.git.operation.failed", StringUtil.capitalize(operationName));
    VcsNotifier.getInstance(project)
      .notifyError(displayId,
                   notificationTitle,
                   getErrorDescription(true),
                   new NotificationListener.Adapter() {
                     @Override
                     protected void hyperlinkActivated(@NotNull Notification notification,
                                                       @NotNull HyperlinkEvent e) {
                       showErrorDialog(project, operationName, changes, absolutePaths);
                     }
                   }
      );
  }

  private static void showErrorDialog(@NotNull Project project, @NotNull String operationName, @NotNull List<? extends Change> changes,
                                      @NotNull Collection<String> absolutePaths) {
    String title = GitBundle.message("dialog.title.local.changes.prevent.from.operation", StringUtil.capitalize(operationName));
    String description = getErrorDescription(false);
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

}

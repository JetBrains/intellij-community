// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.ui.SelectFilesDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import git4idea.DialogManager;
import git4idea.GitNotificationIdsHolder;
import git4idea.GitUtil;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vcs.VcsNotifier.IMPORTANT_ERROR_NOTIFICATION;

public final class GitUntrackedFilesHelper {

  private static final Logger LOG = Logger.getInstance(GitUntrackedFilesHelper.class);

  private GitUntrackedFilesHelper() {
  }

  /**
   * Displays notification about {@code untracked files would be overwritten by checkout} error.
   * Clicking on the link in the notification opens a simple dialog with the list of these files.
   * @param operation   the name of the Git operation that caused the error: {@code rebase, merge, checkout}.
   * @param description the content of the notification or null if the default content is to be used.
   */
  public static void notifyUntrackedFilesOverwrittenBy(@NotNull Project project,
                                                       @NotNull VirtualFile root,
                                                       @NotNull Collection<String> relativePaths,
                                                       @NotNull @Nls String operation,
                                                       @Nullable @NlsContexts.DetailedDescription String description) {
    notifyUntrackedFilesOverwrittenBy(project, root, relativePaths, operation, description, new NotificationAction[0]);
  }

  public static void notifyUntrackedFilesOverwrittenBy(@NotNull Project project,
                                                       @NotNull VirtualFile root,
                                                       @NotNull Collection<String> relativePaths,
                                                       @NotNull @Nls String operation,
                                                       @Nullable @NlsContexts.DetailedDescription String description,
                                                       NotificationAction @NotNull ... actions) {
    Notification notification = getUntrackedFilesOverwrittenByNotification(project, root, relativePaths, operation, description);
    for (NotificationAction action : actions) {
      notification.addAction(action);
    }
    VcsNotifier.getInstance(project).notify(notification);
  }

  /**
   * Show dialog for the "Untracked Files Would be Overwritten by checkout/merge/rebase" error,
   * with a proposal to rollback the action (checkout/merge/rebase) in successful repositories.
   * <p/>
   * The method receives the relative paths to some untracked files, returned by Git command,
   * and tries to find corresponding VirtualFiles, based on the given root, to display in the standard dialog.
   * If for some reason it doesn't find any VirtualFile, it shows the paths in a simple dialog.
   *
   * @return true if the user agrees to rollback, false if the user decides to keep things as is and simply close the dialog.
   */
  public static boolean showUntrackedFilesDialogWithRollback(final @NotNull Project project,
                                                             @NotNull @Nls String operationName,
                                                             @NotNull @NlsContexts.Label String rollbackProposal,
                                                             @NotNull VirtualFile root,
                                                             final @NotNull Collection<String> relativePaths) {
    Collection<String> absolutePaths = GitUtil.toAbsolute(root, relativePaths);
    List<VirtualFile> untrackedFiles = ContainerUtil.mapNotNull(absolutePaths,
                                                                      absolutePath -> GitUtil.findRefreshFileOrLog(absolutePath));

    Ref<Boolean> rollback = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      JComponent filesBrowser;
      if (untrackedFiles.isEmpty()) {
        LOG.debug("Couldn't find the untracked files, displaying simplified dialog.");
        filesBrowser = new GitSimplePathsBrowser(project, absolutePaths);
      }
      else {
        long validFiles = untrackedFiles.stream().filter(VirtualFile::isValid).count();
        LOG.debug(String.format("Untracked files: [%s]. Valid: %d (of %d)", untrackedFiles, validFiles, untrackedFiles.size()));
        filesBrowser = ScrollPaneFactory.createScrollPane(new SelectFilesDialog.VirtualFileList(project, false, true, untrackedFiles));
      }
      String title = GitBundle.message("dialog.title.could.not.operation", StringUtil.capitalize(operationName));
      String description = GitBundle.message("dialog.message.untracked.files.will.be.overwritten.by.operation", operationName);
      DialogWrapper dialog = new UntrackedFilesRollBackDialog(project, filesBrowser, description, rollbackProposal);
      dialog.setTitle(title);
      DialogManager.show(dialog);
      rollback.set(dialog.isOK());
    });
    return rollback.get();
  }

  private static @NotNull Notification getUntrackedFilesOverwrittenByNotification(@NotNull Project project,
                                                                                  @NotNull VirtualFile root,
                                                                                  @NotNull Collection<String> relativePaths,
                                                                                  @NotNull @Nls String operation,
                                                                                  @Nullable @NlsContexts.DetailedDescription String description) {
    if (description == null) description = "";
    String notificationTitle = GitBundle.message("notification.title.untracked.files.prevent.operation", StringUtil.capitalize(operation));
    String notificationDesc = GitBundle.message("notification.content.untracked.files.prevent.operation.move.or.commit",
                                                operation, description);

    final Collection<String> absolutePaths = GitUtil.toAbsolute(root, relativePaths);
    final List<VirtualFile> untrackedFiles = ContainerUtil.mapNotNull(absolutePaths, absolutePath -> {
      return GitUtil.findRefreshFileOrLog(absolutePath);
    });

    Notification notification = IMPORTANT_ERROR_NOTIFICATION
      .createNotification(notificationTitle, notificationDesc, NotificationType.ERROR)
      .setDisplayId(GitNotificationIdsHolder.UNTRACKED_FIES_OVERWITTEN);

    notification.addAction(new NotificationAction(VcsBundle.messagePointer("action.NotificationAction.VFSListener.text.view.files")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        String dialogDesc = GitBundle.message("dialog.message.untracked.files.will.be.overwritten.by.operation", operation);
        String title = GitBundle.message("dialog.title.untracked.files.preventing.operation", StringUtil.capitalize(operation));
        if (untrackedFiles.isEmpty()) {
          GitUtil.showPathsInDialog(project, absolutePaths, title, dialogDesc);
        }
        else {
          DialogWrapper dialog;
          dialog = new UntrackedFilesDialog(project, untrackedFiles, dialogDesc);
          dialog.setTitle(title);
          dialog.show();
        }
      }
    });
    return notification;
  }

  private static class UntrackedFilesDialog extends SelectFilesDialog {

    UntrackedFilesDialog(Project project,
                         @NotNull Collection<? extends VirtualFile> untrackedFiles,
                         @NotNull @NlsContexts.Label String dialogDesc) {
      super(project, new ArrayList<>(untrackedFiles), StringUtil.stripHtml(dialogDesc, true), null, false, true);
      init();
    }

    @Override
    protected Action @NotNull [] createActions() {
      return new Action[]{getOKAction()};
    }
  }

  private static class UntrackedFilesRollBackDialog extends DialogWrapper {
    private final @NotNull JComponent myFilesBrowser;
    private final @NotNull @NlsContexts.Label String myPrompt;
    private final @NotNull @NlsContexts.Label String myRollbackProposal;

    UntrackedFilesRollBackDialog(@NotNull Project project,
                                 @NotNull JComponent filesBrowser,
                                 @NotNull @NlsContexts.Label String prompt,
                                 @NotNull @NlsContexts.Label String rollbackProposal) {
      super(project);
      myFilesBrowser = filesBrowser;
      myPrompt = prompt;
      myRollbackProposal = rollbackProposal;
      setOKButtonText(GitBundle.message("button.rollback"));
      setCancelButtonText(GitBundle.message("button.don.t.rollback"));
      init();
    }

    @Override
    protected JComponent createSouthPanel() {
      JComponent buttons = super.createSouthPanel();
      JPanel panel = new JPanel(new VerticalFlowLayout());
      panel.add(new JBLabel(XmlStringUtil.wrapInHtml(myRollbackProposal)));
      if (buttons != null) {
        panel.add(buttons);
      }
      return panel;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
      return myFilesBrowser;
    }

    @Override
    protected @Nullable JComponent createNorthPanel() {
      JLabel label = new JLabel(myPrompt);
      label.setUI(new MultiLineLabelUI());
      label.setBorder(new EmptyBorder(5, 1, 5, 1));
      return label;
    }
  }
}

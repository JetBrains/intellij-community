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
package git4idea.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ui.SelectFilesDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import git4idea.DialogManager;
import git4idea.GitUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vcs.VcsNotifier.IMPORTANT_ERROR_NOTIFICATION;

public class GitUntrackedFilesHelper {

  private static final Logger LOG = Logger.getInstance(GitUntrackedFilesHelper.class);

  private GitUntrackedFilesHelper() {
  }

  /**
   * Defines notification about {@code untracked files would be overwritten by checkout} error.
   * Clicking on the link in the notification opens a simple dialog with the list of these files.
   * @param root
   * @param relativePaths
   * @param operation   the name of the Git operation that caused the error: {@code rebase, merge, checkout}.
   * @param description the content of the notification or null if the default content is to be used.
   */
  public static Notification notifyUntrackedFilesOverwrittenBy(@NotNull final Project project,
                                                       @NotNull final VirtualFile root, @NotNull Collection<String> relativePaths,
                                                       @NotNull final String operation, @Nullable String description, @Nullable NotificationListener listener) {
    final String notificationTitle = "Untracked files conflict with " + operation;
    final String notificationDesc = description == null ? "Please move or commit them before " + operation : description;

    final Collection<String> absolutePaths = GitUtil.toAbsolute(root, relativePaths);
    final List<VirtualFile> untrackedFiles = ContainerUtil.mapNotNull(absolutePaths,
                                                                      absolutePath -> GitUtil.findRefreshFileOrLog(absolutePath));

    Notification notification = IMPORTANT_ERROR_NOTIFICATION.createNotification(notificationTitle, notificationDesc, NotificationType.ERROR,listener);
    notification.addAction(new NotificationAction("View Files") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        final String dialogDesc = "These untracked files would be overwritten by " + operation;
        String title = "Untracked Files Preventing " + StringUtil.capitalize(operation);
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
  public static boolean showUntrackedFilesDialogWithRollback(@NotNull final Project project,
                                                             @NotNull final String operationName,
                                                             @NotNull final String rollbackProposal,
                                                             @NotNull VirtualFile root,
                                                             @NotNull final Collection<String> relativePaths) {
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
        filesBrowser = ScrollPaneFactory.createScrollPane(new SelectFilesDialog.VirtualFileList(project, untrackedFiles, false, true));
      }
      String title = "Could not " + StringUtil.capitalize(operationName);
      String description = "These untracked files would be overwritten by " + operationName;
      DialogWrapper dialog = new UntrackedFilesRollBackDialog(project, filesBrowser, description, rollbackProposal);
      dialog.setTitle(title);
      DialogManager.show(dialog);
      rollback.set(dialog.isOK());
    });
    return rollback.get();
  }

  private static class UntrackedFilesDialog extends SelectFilesDialog {

    public UntrackedFilesDialog(Project project, Collection<VirtualFile> untrackedFiles, String dialogDesc) {
      super(project, new ArrayList<>(untrackedFiles), StringUtil.stripHtml(dialogDesc, true), null, false, false, true);
      init();
    }

    @NotNull
    @Override
    protected Action[] createActions() {
      return new Action[]{getOKAction()};
    }

  }

  private static class UntrackedFilesRollBackDialog extends DialogWrapper {

    @NotNull private final JComponent myFilesBrowser;
    @NotNull private final String myPrompt;
    @NotNull private final String myRollbackProposal;

    public UntrackedFilesRollBackDialog(@NotNull Project project, @NotNull JComponent filesBrowser, @NotNull String prompt,
                                        @NotNull String rollbackProposal) {
      super(project);
      myFilesBrowser = filesBrowser;
      myPrompt = prompt;
      myRollbackProposal = rollbackProposal;
      setOKButtonText("Rollback");
      setCancelButtonText("Don't rollback");
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

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myFilesBrowser;
    }

    @Nullable
    @Override
    protected JComponent createNorthPanel() {
      JLabel label = new JLabel(myPrompt);
      label.setUI(new MultiLineLabelUI());
      label.setBorder(new EmptyBorder(5, 1, 5, 1));
      return label;
    }
  }
}

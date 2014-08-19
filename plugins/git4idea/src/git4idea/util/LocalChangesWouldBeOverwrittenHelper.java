/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.ui.ChangesBrowserWithRollback;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.List;

public class LocalChangesWouldBeOverwrittenHelper {

  @NotNull
  private static String getErrorNotificationDescription() {
    return getErrorDescription(true);
  }

  @NotNull
  private static String getErrorDialogDescription() {
    return getErrorDescription(false);
  }

  @NotNull
  private static String getErrorDescription(boolean forNotification) {
    String line1 = "Your local changes would be overwritten by merge.";
    String line2 = "Commit, stash or revert them to proceed.";
    if (forNotification) {
      return line1 + "<br/>" + line2 + " <a href='view'>View them</a>";
    }
    else {
      return line1 + "\n" + line2;
    }
  }

  public static void showErrorNotification(@NotNull final Project project, @NotNull final VirtualFile root, @NotNull final String operationName,
                                           @NotNull final Collection<String> relativeFilePaths) {
    final Collection<String> absolutePaths = GitUtil.toAbsolute(root, relativeFilePaths);
    final List<Change> changes = GitUtil.findLocalChangesForPaths(project, root, absolutePaths, false);
    String notificationTitle = "Git " + StringUtil.capitalize(operationName) + " Failed";
    VcsNotifier.getInstance(project).notifyError(notificationTitle, getErrorNotificationDescription(),
      new NotificationListener.Adapter() {
       @Override
       protected void hyperlinkActivated(@NotNull Notification notification,
                                         @NotNull HyperlinkEvent e) {
         showErrorDialog(project, operationName, changes, absolutePaths);
       }
      });
  }

  public static void showErrorDialog(@NotNull Project project, @NotNull VirtualFile root, @NotNull String operationName,
                                     @NotNull Collection<String> relativeFilePaths) {
    Collection<String> absolutePaths = GitUtil.toAbsolute(root, relativeFilePaths);
    List<Change> changes = GitUtil.findLocalChangesForPaths(project, root, absolutePaths, false);
    showErrorDialog(project, operationName, changes, absolutePaths);
  }

  private static void showErrorDialog(@NotNull Project project, @NotNull String operationName, @NotNull List<Change> changes,
                                      @NotNull Collection<String> absolutePaths) {
    String title = "Local Changes Prevent from " + StringUtil.capitalize(operationName);
    String description = getErrorDialogDescription();
    if (changes.isEmpty()) {
      GitUtil.showPathsInDialog(project, absolutePaths, title, description);
    }
    else {
      DialogBuilder builder = new DialogBuilder(project);
      builder.setNorthPanel(new MultiLineLabel(description));
      builder.setCenterPanel(new ChangesBrowserWithRollback(project, changes));
      builder.addOkAction();
      builder.setTitle(title);
      builder.show();
    }
  }

}

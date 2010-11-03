/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.config;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ui.UIUtil;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

/**
 * Project service that is used to check whether currently set git executable is valid (just calls 'git version' and parses the output),
 * and to display notification to the user proposing to fix the project set up.
 * @author Kirill Likhodedov
 */
public class GitExecutableValidator {

  private Notification myNotification;
  private final Project myProject;

  public static GitExecutableValidator getInstance(Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetService(project, GitExecutableValidator.class);
  }

  public GitExecutableValidator(Project project) {
    myProject = project;
  }

  /**
   * Returns true if 'git version' command was executed without errors and outputted a valid Git version information.
   */
  public boolean isGitExecutableValid() {
    try {
      GitVcs.version(myProject);
      return true;
    } catch (VcsException e) {
      return false;
    }
  }

  /**
   * Shows notification that git is not configured with a link to the Settings to fix it.
   * Expires the notification if user fixes the path to Git from the opened Settings dialog.
   */
  public void showExecutableNotConfiguredNotification() {
    final Notification newNotification = new Notification(GitVcs.NOTIFICATION_GROUP_ID, GitBundle.getString("executable.error.title"),
      GitBundle.getString("executable.error.description"), NotificationType.ERROR,
      new NotificationListener() {
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          ShowSettingsUtil.getInstance().showSettingsDialog(myProject, GitVcs.getInstance(myProject).getConfigurable());
          if (isGitExecutableValid()) {
            notification.expire();
          }
        }
      });

    // expire() needs to be called from EventDispatch thread. notify handles it by itself.
    // but we want to be sure that previous notification expires before new one is shown (and assigned to myNotification).
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override public void run() {
        if (myNotification != null && !myNotification.isExpired()) {
          // don't display this notification twice, but better to redisplay it again so that popup appears.
          myNotification.expire();
        }
        myNotification = newNotification;
        Notifications.Bus.notify(myNotification, myProject);
      }
    });
  }

  /**
   * Checks if git executable is valid and displays the notification if not.
   */
  public void checkExecutableAndNotifyIfNeeded() {
    if (!isGitExecutableValid()) {
      showExecutableNotConfiguredNotification();
    }
  }

  /**
   * Checks if git executable is valid. If not (which is a common case for low-level vcs exceptions), shows the
   * notification. Otherwise throws the exception.
   * This is to be used in catch-clauses
   * @param e exception which was thrown.
   * @throws VcsException if git executable is valid.
   */
  public void showNotificationOrThrow(VcsException e) throws VcsException {
    if (!isGitExecutableValid()) {
      showExecutableNotConfiguredNotification();
    } else {
      throw e;
    }
  }
  
}

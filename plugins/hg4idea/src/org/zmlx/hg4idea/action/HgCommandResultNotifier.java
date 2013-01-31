// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.action;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.util.List;

public final class HgCommandResultNotifier {

  private final Project myProject;
  private static final Logger LOG = Logger.getInstance(HgCommandResultNotifier.class);
  public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup(
    "Mercurial Messages", ChangesViewContentManager.TOOLWINDOW_ID, true);
  public static final NotificationGroup IMPORTANT_ERROR_NOTIFICATION = new NotificationGroup(
    "Mercurial Important Messages", NotificationDisplayType.STICKY_BALLOON, true);

  public HgCommandResultNotifier(Project project) {
    myProject = project;
  }

  public void notifySuccess(@NotNull String title, @NotNull String successDescription) {
    NOTIFICATION_GROUP.createNotification(title, successDescription, NotificationType.INFORMATION, null).notify(myProject);
  }

  public void notifyError(@Nullable HgCommandResult result, @NotNull String failureTitle, @NotNull String failureDescription) {
    notifyError(result, failureTitle, failureDescription, null);
  }

  public void notifyError(@Nullable HgCommandResult result,
                          @NotNull String failureTitle,
                          @NotNull String failureDescription,
                          @Nullable NotificationListener listener) {
    List<String> err;
    String errorMessage;
    if (StringUtil.isEmptyOrSpaces(failureDescription)) {
      failureDescription = failureTitle;
    }
    if (result == null) {
      errorMessage = failureDescription;
    } else {
      err = result.getErrorLines();
      if (err.isEmpty()) {
        LOG.assertTrue(!StringUtil.isEmptyOrSpaces(failureDescription),
                       "Failure title, failure description and errors log can not be empty at the same time");
        errorMessage = failureDescription;
      } else if (failureDescription.isEmpty()) {
        errorMessage = "<html>" + StringUtil.join(err, "<br>") + "</html>";
      } else {
        errorMessage = "<html>" + failureDescription + "<br>" + StringUtil.join(err, "<br>") + "</html>";
      }
    }
    IMPORTANT_ERROR_NOTIFICATION
      .createNotification(failureTitle, errorMessage, NotificationType.ERROR, listener)
      .notify(myProject);
  }
}

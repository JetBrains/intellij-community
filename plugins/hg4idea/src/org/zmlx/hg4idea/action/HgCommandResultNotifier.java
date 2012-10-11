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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.vcsUtil.VcsImplUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.util.List;

public final class HgCommandResultNotifier {

  private final Project myProject;

  public HgCommandResultNotifier(Project project) {
    myProject = project;
  }

  public void process(HgCommandResult result, @Nullable String successTitle, @Nullable String successDescription) {
    process(result, successTitle, successDescription, null, null );
  }

  public void process( HgCommandResult result,
                       @Nullable String successTitle, @Nullable String successDescription,
                       @Nullable String failureTitle, @Nullable String failureDescription ){
    List<String> out = result.getOutputLines();
    List<String> err = result.getErrorLines();
    if (!out.isEmpty()) {
      VcsUtil.showStatusMessage(myProject, out.get(out.size() - 1));
    }
    if (HgErrorUtil.isAbort(result)) {
      VcsImplUtil.showErrorMessage(
        myProject, "<html>" + StringUtil.join(err, "<br>") + "</html>", "Error"
      );
    } else if ( result.getExitValue() != 0 && failureTitle != null && failureDescription != null ){
      Notifications.Bus.notify(new Notification(HgVcs.NOTIFICATION_GROUP_ID, failureTitle, failureDescription, NotificationType.ERROR), myProject);
    } else if (successTitle != null && successDescription != null) {
      Notifications.Bus.notify(new Notification(HgVcs.NOTIFICATION_GROUP_ID, successTitle, successDescription, NotificationType.INFORMATION), myProject);
    }
  }
}

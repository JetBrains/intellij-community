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
package git4idea.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.NotificationContent;
import com.intellij.openapi.vcs.VcsNotifier;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class GitTaskResultNotificationHandler extends GitTaskResultHandlerAdapter {
  private final Project myProject;
  private final @NotificationContent String mySuccessMessage;
  private final @NotificationContent String myCancelMessage;
  private final @NotificationContent String myErrorMessage;
  private final @NonNls String myDisplayId;

  public GitTaskResultNotificationHandler(Project project,
                                          @Nullable @NonNls String displayId,
                                          @NotificationContent String successMessage,
                                          @NotificationContent String cancelMessage,
                                          @NotificationContent String errorMessage) {
    myProject = project;
    mySuccessMessage = successMessage;
    myCancelMessage = cancelMessage;
    myErrorMessage = errorMessage;
    myDisplayId = displayId;
  }

  @Override
  protected void onSuccess() {
    VcsNotifier.getInstance(myProject).notifySuccess(myDisplayId, "", mySuccessMessage);
  }

  @Override
  protected void onCancel() {
    VcsNotifier.getInstance(myProject).notifySuccess(myDisplayId, "", myCancelMessage);
  }

  @Override
  protected void onFailure() {
    VcsNotifier.getInstance(myProject).notifyError(myDisplayId, "", myErrorMessage);
  }
}

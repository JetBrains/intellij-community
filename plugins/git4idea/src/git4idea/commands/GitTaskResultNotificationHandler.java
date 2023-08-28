// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.SystemInfo.isMac;

@ApiStatus.Internal
public class LimitHistoryCheck {
  @NotNull private final Project myProject;
  @NotNull private final String myFilePath;
  private int myLimit;
  private int myCount;
  private boolean myWarningShown;

  public LimitHistoryCheck(@NotNull Project project, @NotNull String filePath) {
    myProject = project;
    myFilePath = filePath;
    myWarningShown = false;
    init();
  }

  private void init() {
    VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);
    myLimit = configuration.LIMIT_HISTORY ? configuration.MAXIMUM_HISTORY_ROWS : -1;
    myCount = 0;
  }

  public void checkNumber() {
    if (myLimit <= 0) return;
    ++myCount;
    if (isOver()) {
      if (!myWarningShown) {
        String settingPath = isMac ? VcsBundle.message("vcs.settings.path.mac") : VcsBundle.message("vcs.settings.path");
        String message = VcsBundle.message("file.history.exceeded.limit.message", myLimit, myFilePath, settingPath);
        VcsBalloonProblemNotifier.showOverChangesView(myProject, message, MessageType.WARNING);
        myWarningShown = true;
      }
      throw new VcsFileHistoryLimitReachedException();
    }
  }

  public void reset() {
    init();
  }

  public boolean isOver() {
    return isOver(myCount);
  }

  public boolean isOver(int count) {
    return myLimit > 0 && myLimit < count;
  }

  public static class VcsFileHistoryLimitReachedException extends RuntimeException {
  }
}

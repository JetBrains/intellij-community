// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

public class FileWatchRequestsManager {
  private final FileWatchRequestModifier myModifier;
  private final Alarm myAlarm;

  public FileWatchRequestsManager(@NotNull Project project, @NotNull NewMappings newMappings) {
    this(project, newMappings, LocalFileSystem.getInstance());
  }

  public FileWatchRequestsManager(@NotNull Project project, @NotNull NewMappings newMappings, @NotNull LocalFileSystem localFileSystem) {
    myModifier = new FileWatchRequestModifier(project, newMappings, localFileSystem);
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, newMappings);
  }

  public void ping() {
    if (myAlarm.isDisposed() ||
        ApplicationManager.getApplication().isUnitTestMode()) {
      myModifier.run();
    }
    else {
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(myModifier, 0);
    }
  }
}

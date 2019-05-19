// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RequestsMerger;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;

public class FileWatchRequestsManager {
  private final RequestsMerger myMerger;
  private final Project myProject;

  public FileWatchRequestsManager(@NotNull Project project, @NotNull NewMappings newMappings) {
    this(project, newMappings, LocalFileSystem.getInstance());
  }

  public FileWatchRequestsManager(@NotNull Project project, @NotNull NewMappings newMappings, @NotNull LocalFileSystem localFileSystem) {
    myProject = project;
    myMerger = new RequestsMerger(new FileWatchRequestModifier(project, newMappings, localFileSystem), runnable -> {
      if (!myProject.isInitialized() || myProject.isDisposed()) {
        return;
      }

      Application application = ApplicationManager.getApplication();
      if (application.isUnitTestMode()) {
        runnable.run();
      }
      else {
        application.executeOnPooledThread(runnable);
      }
    });
  }

  public void ping() {
    myMerger.request();
  }
}

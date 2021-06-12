// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;


public class ProjectRootModificationTrackerImpl extends ProjectRootModificationTracker {
  private final ProjectRootManager myManager;

  public ProjectRootModificationTrackerImpl(Project project) {
    myManager = ProjectRootManager.getInstance(project);
  }

  @Override
  public long getModificationCount() {
    return myManager.getModificationCount();
  }
}

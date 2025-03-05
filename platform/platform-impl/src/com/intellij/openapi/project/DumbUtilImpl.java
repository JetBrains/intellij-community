// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DumbUtilImpl implements DumbUtil {
  private final Project myProject;

  public DumbUtilImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public boolean mayUseIndices() {
    return !DumbService.getInstance(myProject).isDumb() || FileBasedIndex.getInstance().getCurrentDumbModeAccessType(myProject) != null;
  }

  public static void waitForSmartMode(@Nullable Project project) {
    if (project != null) {
      DumbService.getInstance(project).waitForSmartMode();
    }
    else {
      for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
        DumbService.getInstance(openProject).waitForSmartMode();
      }
    }
  }
}

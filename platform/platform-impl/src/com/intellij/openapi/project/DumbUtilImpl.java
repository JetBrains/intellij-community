// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

public class DumbUtilImpl implements DumbUtil {
  private final Project myProject;

  public DumbUtilImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public boolean mayUseIndices() {
    return !DumbService.getInstance(myProject).isDumb() || FileBasedIndex.getInstance().getCurrentDumbModeAccessType() != null;
  }
}

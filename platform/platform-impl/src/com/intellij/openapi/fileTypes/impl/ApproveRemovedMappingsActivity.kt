// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

final class ApproveRemovedMappingsActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    RemovedMappingTracker removedMappings = ((FileTypeManagerImpl)FileTypeManager.getInstance()).getRemovedMappingTracker();
    removedMappings.approveUnapprovedMappings();
  }
}

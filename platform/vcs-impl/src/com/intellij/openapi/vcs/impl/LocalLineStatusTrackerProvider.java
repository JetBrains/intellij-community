// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface LocalLineStatusTrackerProvider {
  ExtensionPointName<LocalLineStatusTrackerProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.openapi.vcs.impl.LocalLineStatusTrackerProvider");

  boolean isTrackedFile(@NotNull Project project, @NotNull VirtualFile file);

  boolean isMyTracker(@NotNull LocalLineStatusTracker<?> tracker);

  @Nullable
  LocalLineStatusTracker<?> createTracker(@NotNull Project project, @NotNull VirtualFile file);
}

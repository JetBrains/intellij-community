// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PartialChangesUtil {
  @Nullable
  public static PartialLocalLineStatusTracker getPartialTracker(@NotNull Project project, @NotNull Change change) {
    ContentRevision revision = change.getAfterRevision();
    if (!(revision instanceof CurrentContentRevision)) return null;

    VirtualFile file = ((CurrentContentRevision)revision).getVirtualFile();
    if (file == null) return null;

    LineStatusTracker<?> tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(file);
    return ObjectUtils.tryCast(tracker, PartialLocalLineStatusTracker.class);
  }
}

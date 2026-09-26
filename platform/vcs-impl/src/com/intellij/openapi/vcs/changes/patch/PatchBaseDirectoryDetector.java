// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;


@ApiStatus.Internal
public abstract class PatchBaseDirectoryDetector {
  public static PatchBaseDirectoryDetector getInstance(Project project) {
    return project.getService(PatchBaseDirectoryDetector.class);
  }

  public abstract Collection<VirtualFile> findFiles(String fileName);
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultVcsSymlinkResolver implements VcsSymlinkResolver {
  private final Project myProject;
  private final Mode myMode;

  public DefaultVcsSymlinkResolver(@NotNull Project project) {
    myProject = project;
    RegistryValue value = Registry.get("vcs.resolve.symlinks.for.vcs.operations");
    myMode = switch (value.asString()) {
      case "force_target" -> Mode.FORCE_TARGET;
      case "prefer_target" -> Mode.PREFER_TARGET;
      case "fallback_target" -> Mode.FALLBACK_TARGET;
      default -> Mode.DISABLED;
    };
  }

  @Override
  public boolean isEnabled() {
    return myMode != Mode.DISABLED;
  }

  @Override
  public @Nullable VirtualFile resolveSymlink(@NotNull VirtualFile file) {
    if (myMode == Mode.DISABLED) return file;

    VirtualFile canonicalFile = file.getCanonicalFile();
    if (canonicalFile == null || file.equals(canonicalFile)) {
      return null;
    }

    if (myMode == Mode.FORCE_TARGET) {
      return canonicalFile;
    }

    if (myMode == Mode.PREFER_TARGET) {
      if (ProjectLevelVcsManager.getInstance(myProject).getVcsFor(canonicalFile) != null) {
        return canonicalFile;
      }
    }
    else if (myMode == Mode.FALLBACK_TARGET) {
      if (ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file) == null &&
          ProjectLevelVcsManager.getInstance(myProject).getVcsFor(canonicalFile) != null) {
        return canonicalFile;
      }
    }
    return null;
  }

  private enum Mode {FORCE_TARGET, PREFER_TARGET, FALLBACK_TARGET, DISABLED}
}

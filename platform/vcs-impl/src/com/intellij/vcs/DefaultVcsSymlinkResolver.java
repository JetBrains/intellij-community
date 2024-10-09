// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Internal
public final class DefaultVcsSymlinkResolver implements VcsSymlinkResolver {
  private static final Logger LOG = Logger.getInstance(DefaultVcsSymlinkResolver.class);

  private final Project myProject;
  private final Mode myMode;

  private boolean mySymlinkMappingWasUsed = false;

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
    VirtualFile canonicalFile = resolveFile(file);
    if (canonicalFile != null) {
      logSymlinkMappingWasUsed(file, canonicalFile);
    }
    return canonicalFile;
  }

  private @Nullable VirtualFile resolveFile(@NotNull VirtualFile file) {
    if (myMode == Mode.DISABLED) return null;

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

  private void logSymlinkMappingWasUsed(@NotNull VirtualFile file, @NotNull VirtualFile canonicalFile) {
    if (mySymlinkMappingWasUsed) return;
    mySymlinkMappingWasUsed = true;
    LOG.info("Symlink mapping for VCS is used, original file: " + file + ", canonical file: " + canonicalFile);
  }

  private enum Mode {FORCE_TARGET, PREFER_TARGET, FALLBACK_TARGET, DISABLED}
}

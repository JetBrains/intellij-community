// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.highlighter.WorkspaceFileType;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.project.ProjectKt;
import com.intellij.util.containers.ContainerUtil;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

@ApiStatus.Internal
public final class DefaultIgnoredFileProvider implements IgnoredFileProvider {
  @Override
  public boolean isIgnoredFile(@NotNull Project project, @NotNull FilePath filePath) {
    IProjectStore store = ProjectKt.getStateStore(project);
    if (!ProjectKt.isDirectoryBased(project) && FileUtilRt.extensionEquals(filePath.getPath(), WorkspaceFileType.DEFAULT_EXTENSION)) {
      return true; // *.iws
    }

    if (StringsKt.equals(filePath.getPath(),
                         FileUtil.toSystemIndependentName(store.getWorkspacePath().toString()),
                         !SystemInfo.isFileSystemCaseSensitive)) {
      return true; // workspace.xml
    }

    if (isShelfDirOrInsideIt(filePath, project)) {
      return true; // .idea/shelf
    }

    return false;
  }

  private static boolean isShelfDirOrInsideIt(@NotNull FilePath filePath, @NotNull Project project) {
    String shelfPath = ShelveChangesManager.getShelfPath(project);
    return FileUtil.isAncestor(shelfPath, filePath.getPath(), false);
  }

  @Override
  public @NotNull Set<IgnoredFileDescriptor> getIgnoredFiles(@NotNull Project project) {
    Set<IgnoredFileBean> ignored = new LinkedHashSet<>();

    String shelfPath = ShelveChangesManager.getShelfPath(project);
    ignored.add(IgnoredBeanFactory.ignoreUnderDirectory(shelfPath, project));

    Path workspaceFile = ProjectKt.getStateStore(project).getWorkspacePath();
    ignored.add(IgnoredBeanFactory.ignoreFile(workspaceFile.toString().replace(File.separatorChar, '/'), project));
    return ContainerUtil.unmodifiableOrEmptySet(ignored);
  }

  @Override
  public @NotNull String getIgnoredGroupDescription() {
    return VcsBundle.message("changes.text.default.ignored.files");
  }
}

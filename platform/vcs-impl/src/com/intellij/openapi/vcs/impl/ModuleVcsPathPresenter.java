// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author yole
 */
public class ModuleVcsPathPresenter extends VcsPathPresenter {
  private final Project myProject;

  public ModuleVcsPathPresenter(final Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public String getPresentableRelativePathFor(final VirtualFile file) {
    if (file == null) return "";
    return ReadAction.compute(() -> {
      if (myProject.isDisposed()) return file.getPresentableUrl();
      boolean hideExcludedFiles = Registry.is("ide.hide.excluded.files");
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();

      Module module = fileIndex.getModuleForFile(file, hideExcludedFiles);
      VirtualFile contentRoot = fileIndex.getContentRootForFile(file, hideExcludedFiles);
      if (module == null || contentRoot == null) return file.getPresentableUrl();

      String relativePath = VfsUtilCore.getRelativePath(file, contentRoot, File.separatorChar);
      assert relativePath != null;

      return getPresentableRelativePathFor(module, contentRoot, relativePath);
    });
  }

  @NotNull
  @Override
  public String getPresentableRelativePath(@NotNull final ContentRevision fromRevision, @NotNull final ContentRevision toRevision) {
    final FilePath fromPath = fromRevision.getFile();
    final FilePath toPath = toRevision.getFile();

    // need to use parent path because the old file is already not there
    final VirtualFile fromParent = getParentFile(fromPath);
    final VirtualFile toParent = getParentFile(toPath);

    if (fromParent != null && toParent != null) {
      String moduleResult = ReadAction.compute(() -> {
        if (myProject.isDisposed()) return null;
        final boolean hideExcludedFiles = Registry.is("ide.hide.excluded.files");
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();

        Module fromModule = fileIndex.getModuleForFile(fromParent, hideExcludedFiles);
        Module toModule = fileIndex.getModuleForFile(toParent, hideExcludedFiles);
        if (fromModule == null || toModule == null || fromModule.equals(toModule)) return null;

        VirtualFile fromContentRoot = fileIndex.getContentRootForFile(fromParent, hideExcludedFiles);
        if (fromContentRoot == null) return null;

        String relativePath = VfsUtilCore.getRelativePath(fromParent, fromContentRoot, File.separatorChar);
        assert relativePath != null;

        relativePath += File.separatorChar;
        if (!fromPath.getName().equals(toPath.getName())) {
          relativePath += fromPath.getName();
        }
        return getPresentableRelativePathFor(fromModule, fromContentRoot, relativePath);
      });
      if (moduleResult != null) return moduleResult;
    }

    return PlatformVcsPathPresenter.getPresentableRelativePath(toPath, fromPath);
  }

  @Nullable
  private static VirtualFile getParentFile(@NotNull FilePath path) {
    FilePath parentPath = path.getParentPath();
    return parentPath != null ? parentPath.getVirtualFile() : null;
  }

  private static @NlsContexts.Label @NotNull String getPresentableRelativePathFor(
    @NotNull Module module,
    @NotNull VirtualFile contentRoot,
    @NotNull String relativePath
  ) {
    @NlsContexts.Label StringBuilder result = new StringBuilder();
    result.append("[");
    result.append(module.getName());
    result.append("] ");
    result.append(contentRoot.getName());
    if (!relativePath.isEmpty()) {
      result.append(File.separatorChar);
      result.append(relativePath);
    }
    return result.toString();
  }
}

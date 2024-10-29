// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsImplUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;


@ApiStatus.Internal
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

    final VirtualFile fromParent = VcsImplUtil.findValidParentAccurately(fromPath);
    final VirtualFile toParent = VcsImplUtil.findValidParentAccurately(toPath);

    if (fromParent != null && toParent != null) {
      String moduleResult = ReadAction.compute(() -> {
        if (myProject.isDisposed()) return null;
        final boolean hideExcludedFiles = Registry.is("ide.hide.excluded.files");
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();

        Module fromModule = fileIndex.getModuleForFile(fromParent, hideExcludedFiles);
        Module toModule = fileIndex.getModuleForFile(toParent, hideExcludedFiles);
        if (fromModule == null) return null;

        if (toModule == null || fromModule.equals(toModule)) {
          String relativePath = RelativePathCalculator.computeRelativePath(toPath.getPath(), fromPath.getPath(), true);
          if (relativePath != null) return FileUtilRt.toSystemDependentName(relativePath); //NON-NLS
        }

        VirtualFile fromContentRoot = fileIndex.getContentRootForFile(fromParent, hideExcludedFiles);
        if (fromContentRoot == null) return null;

        String relativePath = VfsUtilCore.getRelativePath(fromParent, fromContentRoot, File.separatorChar);
        if (relativePath == null) return null;

        if (!relativePath.isEmpty()) {
          relativePath += File.separatorChar;
        }
        if (!fromPath.getName().equals(toPath.getName())) {
          relativePath += fromPath.getName();
        }
        return getPresentableRelativePathFor(fromModule, fromContentRoot, relativePath);
      });
      if (moduleResult != null) return moduleResult;
    }

    return PlatformVcsPathPresenter.getPresentableRelativePath(toPath, fromPath);
  }

  private static @NlsContexts.Label @NotNull String getPresentableRelativePathFor(
    @NotNull Module module,
    @NotNull VirtualFile contentRoot,
    @NlsSafe @NotNull String relativePath
  ) {
    return "[" + module.getName() + "] " +
           contentRoot.getName() + File.separatorChar + relativePath;
  }
}

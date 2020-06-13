// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.project;

import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.progress.ProgressManager.checkCanceled;
import static com.intellij.openapi.util.registry.Registry.is;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;

public interface ProjectFileNode {
  /**
   * Returns one of the following identifiers for the node:
   * <dl>
   * <dt>Module</dt><dd>a module to which this file belongs;</dd>
   * <dt>Project</dt><dd>a project indicates that a file does not belong to any module, but is located under the project directory;</dd>
   * <dt>VirtualFile</dt><dd>a topmost directory that contains this file (specifies a tree view without modules).</dd>
   * </dl>
   */
  @NotNull
  Object getRootID();

  @NotNull
  VirtualFile getVirtualFile();

  default boolean contains(@NotNull VirtualFile file, @NotNull AreaInstance area, boolean strict) {
    Object id = getRootID();
    if (id instanceof AreaInstance && !id.equals(area)) return false;
    return isAncestor(getVirtualFile(), file, strict);
  }

  /**
   * Returns a {@link Module} to which the specified {@code file} belongs;
   * or a {@link Project} if the specified {@code file} does not belong to any module, but is located under the base project directory;
   * or {@code null} if the specified {@code file} does not correspond to the given {@code project}
   */
  @Nullable
  static AreaInstance findArea(@NotNull VirtualFile file, @Nullable Project project) {
    checkCanceled(); // ProcessCanceledException if current task is interrupted
    if (project == null || project.isDisposed() || !file.isValid()) return null;
    if (FileTypeRegistry.getInstance().isFileIgnored(file)) return null; // hide ignored files
    if (ScratchFileService.getInstance().getRootType(file) != null) return ApplicationManager.getApplication();
    Module module = ProjectFileIndex.getInstance(project).getModuleForFile(file, false);
    if (module != null) return module.isDisposed() ? null : module;
    if (!is("projectView.show.base.dir")) return null;
    VirtualFile ancestor = findBaseDir(project);
    // file does not belong to any content root, but it is located under the project directory
    return ancestor == null || !isAncestor(ancestor, file, false) ? null : project;
  }

  /**
   * Returns a base directory for the specified {@code project}, or {@code null} if it does not exist.
   */
  @Nullable
  static VirtualFile findBaseDir(@Nullable Project project) {
    if (project == null || project.isDisposed()) return null;
    String path = project.getBasePath();
    return path == null ? null : LocalFileSystem.getInstance().findFileByPath(path);
  }
}

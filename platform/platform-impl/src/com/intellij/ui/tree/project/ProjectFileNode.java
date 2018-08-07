// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.project;

import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.registry.Registry.is;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;

public interface ProjectFileNode {
  @NotNull
  Object getRootID();

  @NotNull
  VirtualFile getVirtualFile();

  default boolean contains(@NotNull VirtualFile file, @NotNull AreaInstance area, boolean strict) {
    Object id = getRootID();
    if (id instanceof AreaInstance && !id.equals(area)) return false;
    return isAncestor(getVirtualFile(), file, strict);
  }

  @Nullable
  static AreaInstance findArea(@NotNull VirtualFile file, @Nullable Project project) {
    if (project == null || project.isDisposed() || !file.isValid()) return null;
    Module module = ProjectFileIndex.getInstance(project).getModuleForFile(file, false);
    if (module != null) return module.isDisposed() ? null : module;
    if (!is("projectView.show.base.dir")) return null;
    VirtualFile ancestor = project.getBaseDir();
    // file does not belong to any content root, but it is located under the project directory and not ignored
    return ancestor == null || FileTypeRegistry.getInstance().isFileIgnored(file) || !isAncestor(ancestor, file, false) ? null : project;
  }
}

/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.projectView.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 */
public class ProjectRootsUtil {
  private ProjectRootsUtil() { }

  public static boolean isSourceRoot(final PsiDirectory psiDirectory) {
    return isSourceRoot(psiDirectory.getVirtualFile(), psiDirectory.getProject());
  }

  public static boolean isSourceRoot(final VirtualFile directoryFile, final Project project) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return directoryFile.equals(fileIndex.getSourceRootForFile(directoryFile));
  }

  public static boolean isInSource(@NotNull PsiDirectory directory) {
    return isInSource(directory.getVirtualFile(), directory.getProject());
  }

  public static boolean isInSource(@NotNull VirtualFile directoryFile, @NotNull Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return projectFileIndex.isInSourceContent(directoryFile);
  }

  public static boolean isInTestSource(@NotNull PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    return vFile != null && isInTestSource(vFile, file.getProject());
  }

  public static boolean isInTestSource(@NotNull VirtualFile directoryFile, @NotNull Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return projectFileIndex.isInTestSourceContent(directoryFile);
  }

  public static boolean isModuleSourceRoot(@NotNull VirtualFile virtualFile, @NotNull final Project project) {
    return getModuleSourceRoot(virtualFile, project) != null;
  }

  @Nullable
  public static SourceFolder getModuleSourceRoot(@NotNull VirtualFile root, @NotNull Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module module = projectFileIndex.getModuleForFile(root);
    return module != null && !module.isDisposed() ? findSourceFolder(module, root) : null;
  }

  public static boolean isLibraryRoot(@NotNull VirtualFile directoryFile, @NotNull Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (projectFileIndex.isInLibraryClasses(directoryFile)) {
      final VirtualFile parent = directoryFile.getParent();
      return parent == null || !projectFileIndex.isInLibraryClasses(parent);
    }
    return false;
  }

  public static boolean isModuleContentRoot(@NotNull PsiDirectory directory) {
    return isModuleContentRoot(directory.getVirtualFile(), directory.getProject());
  }

  public static boolean isModuleContentRoot(@NotNull final VirtualFile directoryFile, @NotNull Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile contentRootForFile = projectFileIndex.getContentRootForFile(directoryFile);
    return directoryFile.equals(contentRootForFile);
  }

  public static String findUnloadedModuleByContentRoot(@NotNull final VirtualFile root, @NotNull Project project) {
    final DirectoryInfo info = DirectoryIndex.getInstance(project).getInfoForFile(root);
    if (info.isExcluded(root) && root.equals(info.getContentRoot()) && info.getUnloadedModuleName() != null) {
      return info.getUnloadedModuleName();
    }
    return null;
  }

  public static String findUnloadedModuleByFile(@NotNull final VirtualFile file, @NotNull Project project) {
    DirectoryInfo info = DirectoryIndex.getInstance(project).getInfoForFile(file);
    VirtualFile contentRoot = info.getContentRoot();
    if (info.isExcluded(file) && contentRoot != null) {
      DirectoryInfo rootInfo = DirectoryIndex.getInstance(project).getInfoForFile(contentRoot);
      return rootInfo.getUnloadedModuleName();
    }
    return null;
  }

  public static boolean isProjectHome(@NotNull PsiDirectory psiDirectory) {
    return psiDirectory.getVirtualFile().equals(psiDirectory.getProject().getBaseDir());
  }

  public static boolean isOutsideSourceRoot(@Nullable PsiFile psiFile) {
    if (psiFile == null) return false;
    if (psiFile instanceof PsiCodeFragment) return false;
    final VirtualFile file = psiFile.getVirtualFile();
    if (file == null) return false;
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(psiFile.getProject()).getFileIndex();
    return !projectFileIndex.isInSource(file) && !projectFileIndex.isInLibraryClasses(file);
  }

  @Nullable
  public static SourceFolder findSourceFolder(@NotNull Module module, @NotNull VirtualFile root) {
    for (ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
      for (SourceFolder folder : entry.getSourceFolders()) {
        if (root.equals(folder.getFile())) {
          return folder;
        }
      }
    }
    return null;
  }

  @Nullable
  public static ExcludeFolder findExcludeFolder(@NotNull Module module, @NotNull VirtualFile root) {
    for (ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
      for (ExcludeFolder folder : entry.getExcludeFolders()) {
        if (root.equals(folder.getFile())) {
          return folder;
        }
      }
    }
    return null;
  }
}
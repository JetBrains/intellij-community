// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 */
public final class ProjectRootsUtil {
  private ProjectRootsUtil() { }

  public static boolean isSourceRoot(@NotNull PsiDirectory psiDirectory) {
    return isSourceRoot(psiDirectory.getVirtualFile(), psiDirectory.getProject());
  }

  public static boolean isSourceRoot(@NotNull VirtualFile directoryFile, @NotNull Project project) {
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
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
    return fileIndex.isInSourceContent(virtualFile) && virtualFile.equals(fileIndex.getSourceRootForFile(virtualFile));
  }

  @Nullable
  public static SourceFolder getModuleSourceRoot(@NotNull VirtualFile root, @NotNull Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (!root.equals(projectFileIndex.getSourceRootForFile(root))) return null;
    
    final Module module = projectFileIndex.getModuleForFile(root);
    if (module == null || module.isDisposed()) return null;

    VirtualFile contentRoot = projectFileIndex.getContentRootForFile(root);
    if (contentRoot == null) return null;
    
    for (ContentEntry contentEntry : ModuleRootManager.getInstance(module).getContentEntries()) {
      if (contentRoot.equals(contentEntry.getFile())) {
        /*
         If there are several source roots pointing to the same directory, DirectoryIndex::getSourceFolder will return the last of them.
         It appears that we have code which relies on this behavior, so we'll temporarily keep it. 
        */
        SourceFolder @NotNull [] folders = contentEntry.getSourceFolders();
        for (int i = folders.length - 1; i >= 0; i--) {
          SourceFolder folder = folders[i];
          if (root.equals(folder.getFile())) {
            return folder;
          }
        }
      }
    }

    return null;
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

  @Nullable
  public static String findUnloadedModuleByContentRoot(@NotNull final VirtualFile root, @NotNull Project project) {
    if (project.isDefault()) return null;
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
    if (fileIndex.isExcluded(root) && root.equals(fileIndex.getContentRootForFile(root, false))) {
      return fileIndex.getUnloadedModuleNameForFile(root);
    }
    return null;
  }

  public static String findUnloadedModuleByFile(@NotNull final VirtualFile file, @NotNull Project project) {
    if (project.isDefault()) return null;
    return ProjectFileIndex.getInstance(project).getUnloadedModuleNameForFile(file);
  }

  public static boolean isProjectHome(@NotNull PsiDirectory psiDirectory) {
    return psiDirectory.getVirtualFile().equals(psiDirectory.getProject().getBaseDir());
  }

  public static boolean isOutsideSourceRoot(@NotNull PsiFile psiFile) {
    if (psiFile instanceof PsiCodeFragment) return false;
    final VirtualFile file = psiFile.getVirtualFile();
    if (file == null) return false;
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(psiFile.getProject()).getFileIndex();
    return !projectFileIndex.isInSource(file) && !projectFileIndex.isInLibraryClasses(file);
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
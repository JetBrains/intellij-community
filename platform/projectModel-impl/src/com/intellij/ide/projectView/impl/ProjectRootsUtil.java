/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 * Date: 17-Jan-2008
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

  public static boolean isInSource(final PsiDirectory directory) {
    return isInSource(directory.getVirtualFile(), directory.getProject());
  }

  public static boolean isInSource(final VirtualFile directoryFile, final Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return projectFileIndex.isInSourceContent(directoryFile);
  }

  public static boolean isInTestSource(final PsiDirectory psiDirectory) {
    return isInTestSource(psiDirectory.getVirtualFile(), psiDirectory.getProject());
  }

  public static boolean isInTestSource(final VirtualFile directoryFile, final Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return projectFileIndex.isInTestSourceContent(directoryFile);
  }

  public static boolean isSourceOrTestRoot(@NotNull VirtualFile virtualFile, final Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module module = projectFileIndex.getModuleForFile(virtualFile);
    if (module == null) return false;
    final ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    for (ContentEntry contentEntry : contentEntries) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        if (Comparing.equal(virtualFile, sourceFolder.getFile())) return true;
      }
    }
    return false;
  }

  public static boolean isLibraryRoot(final VirtualFile directoryFile, final Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (projectFileIndex.isInLibraryClasses(directoryFile)) {
      final VirtualFile parent = directoryFile.getParent();
      return parent == null || !projectFileIndex.isInLibraryClasses(parent);
    }
    return false;
  }

  public static boolean isModuleContentRoot(final PsiDirectory directory) {
    return isModuleContentRoot(directory.getVirtualFile(), directory.getProject());
  }

  public static boolean isModuleContentRoot(@NotNull final VirtualFile directoryFile, final Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile contentRootForFile = projectFileIndex.getContentRootForFile(directoryFile);
    return directoryFile.equals(contentRootForFile);
  }

  public static boolean isProjectHome(final PsiDirectory psiDirectory) {
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
}
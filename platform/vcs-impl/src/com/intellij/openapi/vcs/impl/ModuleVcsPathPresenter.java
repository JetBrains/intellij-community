/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
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

  @Override
  public String getPresentableRelativePathFor(final VirtualFile file) {
    if (file == null) return "";
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        boolean hideExcludedFiles = Registry.is("ide.hide.excluded.files");
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();

        Module module = fileIndex.getModuleForFile(file, hideExcludedFiles);
        VirtualFile contentRoot = fileIndex.getContentRootForFile(file, hideExcludedFiles);
        if (module == null || contentRoot == null) return file.getPresentableUrl();

        String relativePath = VfsUtilCore.getRelativePath(file, contentRoot, File.separatorChar);
        assert relativePath != null;

        return getPresentableRelativePathFor(module, contentRoot, relativePath);
      }
    });
  }

  @Override
  public String getPresentableRelativePath(@NotNull final ContentRevision fromRevision, @NotNull final ContentRevision toRevision) {
    final FilePath fromPath = fromRevision.getFile();
    final FilePath toPath = toRevision.getFile();

    // need to use parent path because the old file is already not there
    final VirtualFile fromParent = getParentFile(fromPath);
    final VirtualFile toParent = getParentFile(toPath);

    if (fromParent != null && toParent != null) {
      String moduleResult = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Override
        public String compute() {
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
        }
      });
      if (moduleResult != null) return moduleResult;
    }

    final RelativePathCalculator calculator = new RelativePathCalculator(toPath.getPath(), fromPath.getPath());
    calculator.execute();
    final String result = calculator.getResult();
    return result != null ? result.replace("/", File.separator) : null;
  }

  @Nullable
  private static VirtualFile getParentFile(@NotNull FilePath path) {
    FilePath parentPath = path.getParentPath();
    return parentPath != null ? parentPath.getVirtualFile() : null;
  }

  @NotNull
  private static String getPresentableRelativePathFor(@NotNull final Module module,
                                                      @NotNull final VirtualFile contentRoot,
                                                      @NotNull final String relativePath) {
    StringBuilder result = new StringBuilder();
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

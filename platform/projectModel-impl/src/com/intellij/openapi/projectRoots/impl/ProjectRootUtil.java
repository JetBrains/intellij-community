/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.file.impl.FileManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
public class ProjectRootUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectRootUtil");

  private ProjectRootUtil() {
  }

  @NotNull
  private static PsiDirectory[] convertRoots(final Project project, VirtualFile[] roots) {
    return convertRoots(((PsiManagerImpl)PsiManager.getInstance(project)).getFileManager(), roots);
  }

  @NotNull
  private static PsiDirectory[] convertRoots(final FileManager fileManager, VirtualFile[] roots) {
    List<PsiDirectory> dirs = new ArrayList<>();

    for (VirtualFile root : roots) {
      if (!root.isValid()) {
        LOG.error("Root " + root + " is not valid!");
      }
      PsiDirectory dir = fileManager.findDirectory(root);
      if (dir != null) {
        dirs.add(dir);
      }
    }

    return dirs.toArray(new PsiDirectory[dirs.size()]);
  }

  @NotNull
  public static PsiDirectory[] getSourceRootDirectories(final Project project) {
    VirtualFile[] files = OrderEnumerator.orderEntries(project).sources().usingCache().getRoots();
    return convertRoots(project, files);
  }

  @NotNull
  public static PsiDirectory[] getAllContentRoots(final Project project) {
    VirtualFile[] files = ProjectRootManager.getInstance(project).getContentRootsFromAllModules();
    return convertRoots(project, files);
  }
}

/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 25-Mar-2010
 */
package org.jetbrains.idea.eclipse.conversion;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.importWizard.EclipseProjectFinder;

import java.io.File;
import java.util.List;

public class ERelativePathUtil {
  private ERelativePathUtil() {
  }

  /**
   * @param path path in format /module_root/relative_path
   * @return module_root
   */
  @NotNull
  public static String getRootPath(String path) {
    int secondSlIdx = path.indexOf('/', 1);
    return secondSlIdx > 1 ? path.substring(1, secondSlIdx) : path.substring(1);
  }

  /**
   * @param path path in format /module_root/relative_path
   * @return relative_path or null if /module_root
   */
  @Nullable
  public static String getRelativeToRootPath(String path) {
    final int secondSlIdx = path.indexOf('/', 1);
    return secondSlIdx != -1 && secondSlIdx + 1 < path.length() ? path.substring(secondSlIdx + 1) : null;
  }

  @Nullable
  public static String relativeToContentRoots(final @NotNull List<String> currentRoots,
                                              final @NotNull String rootPath,
                                              final @Nullable String relativeToRootPath) {
    for (String currentRoot : currentRoots) {
      if (currentRoot.endsWith(rootPath)
          || Comparing.strEqual(rootPath, EclipseProjectFinder.findProjectName(currentRoot))) { //rootPath = content_root <=> applicable root: abs_path/content_root
        if (relativeToRootPath == null) {
          return VfsUtil.pathToUrl(currentRoot);
        }
        final File relativeToOtherModuleFile = new File(currentRoot, relativeToRootPath);
        if (relativeToOtherModuleFile.exists()) {
          return VfsUtil.pathToUrl(relativeToOtherModuleFile.getPath());
        }
      }
    }
    return null;
  }

  /**
   * @param otherModule check file relative to module content root
   * @param relativeToOtherModule local path (paths inside jars are rejected)
   * @return url
   */
  @Nullable
  public static String relativeToOtherModule(final @NotNull Module otherModule, final @Nullable String relativeToOtherModule) {
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(otherModule).getContentRoots();
    for (VirtualFile contentRoot : contentRoots) {
      if (relativeToOtherModule == null) {
        return contentRoot.getUrl();
      }
      final VirtualFile fileUnderModuleContentRoot = contentRoot.findFileByRelativePath(relativeToOtherModule);
      if (fileUnderModuleContentRoot != null) {
        return fileUnderModuleContentRoot.getUrl();
      }
    }
    return null;
  }

  @Nullable
  public static String relativeToOtherModule(final @NotNull Project project, final @NotNull VirtualFile file) {
    final Module module = ModuleUtil.findModuleForFile(file, project);
    if (module != null) {
      final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      for (VirtualFile otherRoot : contentRoots) {
        if (VfsUtil.isAncestor(otherRoot, file, false)) {
          return "/" + module.getName() + "/" + VfsUtil.getRelativePath(file, otherRoot, '/');
        }
      }
    }
    return null;
  }

  @Nullable
  public static VirtualFile getContentRoot(final ModuleRootModel model) {
   final VirtualFile[] contentRoots = model.getContentRoots();
    for (VirtualFile virtualFile : contentRoots) {
      if (virtualFile.findChild(EclipseXml.PROJECT_FILE) != null) {
        return virtualFile;
      }
    }
    return null;
  }
}
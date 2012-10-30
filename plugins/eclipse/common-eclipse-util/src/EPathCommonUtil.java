/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.eclipse;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * User: anna
 * Date: 10/29/12
 */
public class EPathCommonUtil {
  /**
   * @param path path in format /module_root/relative_path
   * @return module_root
   */
  @NotNull
  public static String getRelativeModuleName(String path) {
    int secondSlIdx = path.indexOf('/', 1);
    return secondSlIdx > 1 ? path.substring(1, secondSlIdx) : path.substring(1);
  }

  /**
   * @param path path in format /module_root/relative_path
   * @return relative_path or null if /module_root
   */
  @Nullable
  public static String getRelativeToModulePath(String path) {
    final int secondSlIdx = path.indexOf('/', 1);
    return secondSlIdx != -1 && secondSlIdx + 1 < path.length() ? path.substring(secondSlIdx + 1) : null;
  }

  @Nullable
  public static String expandEclipseRelative2ContentRoots(final @NotNull List<String> currentRoots,
                                                          final @NotNull String rootPath,
                                                          final @Nullable String relativeToRootPath) {
    for (String currentRoot : currentRoots) {
      if (currentRoot.endsWith(rootPath)
          || Comparing.strEqual(rootPath, EclipseProjectFinder.findProjectName(currentRoot))) { //rootPath = content_root <=> applicable root: abs_path/content_root
        if (relativeToRootPath == null) {
          return pathToUrl(currentRoot);
        }
        final File relativeToOtherModuleFile = new File(currentRoot, relativeToRootPath);
        if (relativeToOtherModuleFile.exists()) {
          return pathToUrl(relativeToOtherModuleFile.getPath());
        }
      }
    }
    return null;
  }

  public static String pathToUrl(String path) {
    return "file://" + path;
  }
}

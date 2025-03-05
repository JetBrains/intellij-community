// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public final class EPathCommonUtil {
  /**
   * @param path path in format /module_root/relative_path
   * @return module_root
   */
  public static @NotNull String getRelativeModuleName(String path) {
    int secondSlIdx = path.indexOf('/', 1);
    return secondSlIdx > 1 ? path.substring(1, secondSlIdx) : path.substring(1);
  }

  /**
   * @param path path in format /module_root/relative_path
   * @return relative_path or null if /module_root
   */
  public static @Nullable String getRelativeToModulePath(String path) {
    final int secondSlIdx = path.indexOf('/', 1);
    return secondSlIdx != -1 && secondSlIdx + 1 < path.length() ? path.substring(secondSlIdx + 1) : null;
  }

  public static @Nullable String expandEclipseRelative2ContentRoots(final @NotNull List<String> currentRoots,
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

  public static @NonNls String pathToUrl(String path) {
    return "file://" + FileUtil.toSystemIndependentName(path);
  }
}

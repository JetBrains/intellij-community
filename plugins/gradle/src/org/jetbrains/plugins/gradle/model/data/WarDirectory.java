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
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 2/10/14
 */
public enum WarDirectory {
  /**
   * Public files typically include the following:
   * HTML files.
   * JSP files.
   * Image files and other multimedia files -- it is a common convention to store image files in an images subdirectory.
   */
  WAR_ROOT("/"),
  /**
   * directory can contain the following file:
   * META-INF/MANIFEST.MF -- an optional file that can be used to specify additional meta-information for the WAR.
   */
  META_INF("/META-INF"),
  /**
   * Directory contains a Web archive's private files and directories.
   * That is, when the Web archive is deployed, the files and directories under the WEB-INF/ directory cannot be accessed directly by Web clients.
   */
  WEB_INF("/WEB-INF"),
  /**
   * Subdirectory can store JAR files used by the Web module.
   * The JAR files in this directory are automatically accessible to the Web module without needing to be added to the class path.
   */
  WEB_INF_LIB("/WEB-INF/lib"),
  /**
   * Subdirectory contains the compiled Java code for the Web module.
   */
  WEB_INF_CLASSES("/WEB-INF/classes");
  @NotNull
  private final String myRelativePath;

  WarDirectory(@NotNull final String relativePath) {
    myRelativePath = relativePath;
  }

  @NotNull
  public String getRelativePath() {
    return myRelativePath;
  }

  @Nullable
  public static WarDirectory fromPath(final @NotNull String path) {
    if (StringUtil.isEmpty(path)) return WAR_ROOT;

    final String adjustedPath = path.charAt(0) != '/' ? '/' + path : path;
    for (WarDirectory warDirectory : values()) {
      if (warDirectory.myRelativePath.equals(adjustedPath)) return warDirectory;
    }
    return null;
  }
}

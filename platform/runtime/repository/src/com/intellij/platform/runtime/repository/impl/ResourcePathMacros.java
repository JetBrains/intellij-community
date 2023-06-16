// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Provides support for macro expansion when running from sources. These macros aren't supposed to be used in production mode.
 */
final class ResourcePathMacros {
  private static final String DEFAULT_MAVEN_REPOSITORY_PATH = ".m2/repository";
  private static final String PROJECT_DIR_MACRO = "$PROJECT_DIR$";
  private static final String MAVEN_REPO_MACRO = "$MAVEN_REPOSITORY$";
  private static Path ourProjectDirPath;
  private static Path ourMavenRepositoryPath;

  private static Path getProjectDirPath() {
    if (ourProjectDirPath == null) {
      //todo improve
      ourProjectDirPath = Path.of("").toAbsolutePath();
    }
    return ourProjectDirPath;
  }

  private static Path getMavenRepositoryPath() {
    if (ourMavenRepositoryPath == null) {
      String userHome = System.getProperty("user.home", null);
      Path path = userHome != null ? Path.of(userHome, DEFAULT_MAVEN_REPOSITORY_PATH) : Path.of(DEFAULT_MAVEN_REPOSITORY_PATH);
      ourMavenRepositoryPath = path.toAbsolutePath();
    }
    return ourMavenRepositoryPath;
  }

  static @NotNull Path resolve(@NotNull String path) {
    Path root;
    int startIndex;
    if (path.startsWith(PROJECT_DIR_MACRO)) {
      root = getProjectDirPath();
      startIndex = PROJECT_DIR_MACRO.length();
    }
    else if (path.startsWith(MAVEN_REPO_MACRO)) {
      root = getMavenRepositoryPath();
      startIndex = MAVEN_REPO_MACRO.length();
    }
    else {
      throw new MalformedRepositoryException("Unknown macro in " + path);
    }
    if (path.length() <= startIndex + 1 || path.charAt(startIndex) != '/') {
      throw new MalformedRepositoryException("Incorrect macro usage in " + path);
    }
    if (path.contains("/../") || path.endsWith("/..")) {
      throw new MalformedRepositoryException("Path '" + path + "' points to a file outside macro directory");
    }
    return root.resolve(path.substring(startIndex + 1));
  }
}

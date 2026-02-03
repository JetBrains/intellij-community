// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
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

  private static Path getProjectDirPath(@NotNull Path baseDir) {
    if (ourProjectDirPath == null) {
      ourProjectDirPath = locateProjectHome(baseDir);
    }
    return ourProjectDirPath;
  }

  private static @NotNull Path locateProjectHome(@NotNull Path baseDir) {
    String explicitHomePath = System.getProperty("idea.home.path");
    if (explicitHomePath != null) {
      return Path.of(explicitHomePath);
    }
    
    /* This part is used when the process is started locally from sources (when tests are started from the build scripts, the home path
       is specified explicitly via property). 
       It's supposed to 'baseDir' refers to the main repository located under <project-home>/out/classes. */
    Path currentPath = baseDir;
    while (currentPath != null && !isProjectHome(currentPath)) {
      currentPath = currentPath.getParent();
    }
    if (currentPath == null) {
      throw new MalformedRepositoryException("Cannot find project home to resolve " + PROJECT_DIR_MACRO + " macro, starting from " + baseDir);
    }
    return currentPath.toAbsolutePath();
  }

  private static boolean isProjectHome(@NotNull Path path) {
    return Files.isDirectory(path.resolve(".idea")) && 
           (Files.exists(path.resolve("intellij.idea.ultimate.main.iml")) 
            || Files.exists(path.resolve("intellij.idea.community.main.iml"))
            || Files.exists(path.resolve(".ultimate.root.marker")));
  }

  private static Path getMavenRepositoryPath() {
    if (ourMavenRepositoryPath == null) {
      String userHome = System.getProperty("user.home", null);
      Path path = userHome != null ? Path.of(userHome, DEFAULT_MAVEN_REPOSITORY_PATH) : Path.of(DEFAULT_MAVEN_REPOSITORY_PATH);
      ourMavenRepositoryPath = path.toAbsolutePath();
    }
    return ourMavenRepositoryPath;
  }

  static @NotNull Path resolve(@NotNull String path, @NotNull Path baseDir) {
    Path root;
    int startIndex;
    if (path.startsWith(PROJECT_DIR_MACRO)) {
      root = getProjectDirPath(baseDir);
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

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.roots.VcsRootDetector;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Provides methods to check if the given directory is a root of the given VCS. This is used e.g. by the {@link VcsRootDetector}
 * to detect invalid roots (registered in the settings, but not related to real VCS roots on disk)
 * and unregistered roots (real roots on disk not registered in the settings).
 */
public abstract class VcsRootChecker {
  public static final ExtensionPointName<VcsRootChecker> EXTENSION_POINT_NAME = new ExtensionPointName<>("com.intellij.vcsRootChecker");

  /**
   * Checks if the given path represents a root of the supported VCS.
   */
  public boolean isRoot(@NotNull VirtualFile file) {
    return isRoot(file.getPath());
  }

  /**
   * @deprecated Override {@link #isRoot(VirtualFile)}
   */
  @Deprecated
  public boolean isRoot(@NotNull String path) {
    Logger.getInstance(VcsRootChecker.class).warn("Deprecated API used in " + this, new Throwable());
    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
    return file != null && isRoot(file);
  }

  /**
   * Checks if registered mapping can be used to perform VCS operations.
   * The difference with {@link #isRoot} is that this method should return {@code true} if unsure.
   */
  public boolean validateRoot(@NotNull VirtualFile file) {
    return isRoot(file);
  }

  /**
   * @deprecated Override {@link #validateRoot(VirtualFile)}
   */
  @Deprecated
  public boolean validateRoot(@NotNull String path) {
    return isRoot(path);
  }

  /**
   * Returns the VCS supported by this checker.
   */
  public abstract @NotNull VcsKey getSupportedVcs();

  /**
   * Checks if the given directory looks like a VCS special directory, e.g. "{@code .git}".
   * <br/><br/>
   * This is a quick rough check. A more precise is done in {@link #isRoot(VirtualFile)}.
   */
  public boolean isVcsDir(@NotNull String dirName) {
    return false;
  }

  /**
   * Check if the given directory is ignored in the given VCS root.
   * Such situation can happen, when we detect a VCS root above the directory: in that case we should detect the root only if the directory
   * is not ignored from that root (e.g. the root is the home directory, and the VCS is used for storing configs, ignoring everything else).
   */
  public boolean isIgnored(@NotNull VirtualFile root, @NotNull VirtualFile checkForIgnore) {
    return false;
  }

  /**
   * Check if a given VCS root has dependent directories, that should be checked even if not a part of the Project.
   */
  @NotNull
  public List<VirtualFile> suggestDependentRoots(@NotNull VirtualFile vcsRoot) {
    return Collections.emptyList();
  }

  /**
   * @return Whether any descendant of VCS root can be registered as valid VCS mapping.
   */
  public boolean areChildrenValidMappings() {
    return false;
  }

  /**
   * @param projectRoots - directories with project files
   * @param mappedDirs   - roots that have an explicit mappings, and should not be included into detection
   * @return Detected vcs root mappings for the project
   * or null if default logic should be used instead (relying on {@link #isRoot} calls).
   * @throws VcsException - when detection of project mappings failed (e.g., no internet connection for VCSs that require it).
   */
  @Nullable
  public Collection<VirtualFile> detectProjectMappings(@NotNull Project project,
                                                       @NotNull Collection<VirtualFile> projectRoots,
                                                       @NotNull Set<VirtualFile> mappedDirs) throws VcsException {
    return null;
  }
}

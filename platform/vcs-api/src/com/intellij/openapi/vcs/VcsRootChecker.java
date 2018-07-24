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
package com.intellij.openapi.vcs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.roots.VcsRootDetector;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

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
  public boolean isRoot(@NotNull String path) {
    return false;
  }

  /**
   * Returns the VCS supported by this checker.
   */
  @NotNull
  public abstract VcsKey getSupportedVcs();

  /**
   * Checks if the given path represents the VCS special directory, e.g. {@code .git}.
   */
  public boolean isVcsDir(@NotNull String path) {
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
}

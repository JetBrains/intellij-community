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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Checks VCS roots, revealing invalid roots (registered in the settings, but not related to real VCS roots on disk)
 * and unregistered roots (real roots on disk not registered in the settings).
 *
 * @author Kirill Likhodedov
 */
public abstract class VcsRootChecker {

  @NonNls public static final ExtensionPointName<VcsRootChecker> EXTENSION_POINT_NAME =
    new ExtensionPointName<>("com.intellij.vcsRootChecker");

  /**
   * @param path path to check if it is vcs root directory
   * @return true if it is vcs root
   */
  public boolean isRoot(@NotNull String path) {
    return false;
  }

  /**
   * @return - return vcs for current checker
   */
  public abstract VcsKey getSupportedVcs();

  /**
   * Check if the "dot" directory changed during scan
   *
   * @param path - path to check
   * @return true if it is a DOT_DIR
   */
  public boolean isVcsDir(@Nullable String path) {
    return false;
  }
}

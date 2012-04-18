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

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Checks VCS roots, revealing invalid roots (registered in the settings, but not related to real VCS roots on disk)
 * and unregistered roots (real roots on disk not registered in the settings).
 *
 * @author Kirill Likhodedov
 */
public interface VcsRootChecker {

  /**
   * @return Paths to VCS roots which are not registered in the Settings | Version Control.
   */
  @NotNull
  Collection<String> getUnregisteredRoots();

  /**
   *
   * @param directory root to be checked.
   * @return true if the given directory is not a VCS root.
   */
  boolean isInvalidMapping(@NotNull VcsDirectoryMapping mapping);
}

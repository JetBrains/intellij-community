// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.vcs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Initializes a VCS repository in the specified place.
 */
public interface VcsRepositoryInitializer {
  ExtensionPointName<VcsRepositoryInitializer> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.vcsRepositoryInitializer");

  /**
   * Initializes a new repository in the specified root directory.
   */
  void initRepository(@NotNull File rootDir) throws VcsException;

  /**
   * Returns the VCS which this initializer works for.
   */
  @NotNull
  VcsKey getSupportedVcs();
}

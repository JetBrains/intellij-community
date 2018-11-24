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
package org.jetbrains.plugins.gradle.model;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 7/14/2014
 */
public interface ExternalSourceDirectorySet extends Serializable {
  @NotNull
  String getName();

  @NotNull
  Set<File> getSrcDirs();

  @NotNull
  File getOutputDir();

  @NotNull
  File getGradleOutputDir();

  /**
   * Returns <code>true</code> if compiler output for this ExternalSourceDirectorySet should is inherited from IDEA project
   * @return true if compiler output path is inherited, false otherwise
   */
  boolean isCompilerOutputPathInherited();

  @NotNull
  Set<String> getExcludes();
  @NotNull
  Set<String> getIncludes();

  @NotNull
  List<ExternalFilter> getFilters();
}

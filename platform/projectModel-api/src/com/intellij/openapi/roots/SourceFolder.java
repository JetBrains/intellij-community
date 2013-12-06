/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

/**
 * Represents a source or test source root under the content root of a module.
 *
 * @author dsl
 * @see ContentEntry#getSourceFolders()
 */
public interface SourceFolder extends ContentFolder {
  /**
   * Checks if this root is a production or test source root.
   *
   * @return true if this source root is a test source root, false otherwise.
   */
  boolean isTestSource();

  /**
   * Returns the package prefix for this source root.
   *
   * @return the package prefix, or an empty string if the root has no package prefix.
   */
  @NotNull
  String getPackagePrefix();

  /**
   * Sets the package prefix for this source root.
   *
   * @param packagePrefix the package prefix, or an empty string if the root has no package prefix.
   */
  void setPackagePrefix(@NotNull String packagePrefix);

  @NotNull
  JpsModuleSourceRootType<?> getRootType();

  @NotNull
  JpsModuleSourceRoot getJpsElement();
}

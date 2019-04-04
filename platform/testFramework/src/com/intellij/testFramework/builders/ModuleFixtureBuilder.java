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

package com.intellij.testFramework.builders;

import com.intellij.testFramework.fixtures.ModuleFixture;

/**
 * @author mike
 */
public interface ModuleFixtureBuilder<T extends ModuleFixture> {

  /**
   * Add content root to the module configuration.
   *
   * @param contentRootPath path to your content folder
   * @return current builder
   */
  ModuleFixtureBuilder<T> addContentRoot(final String contentRootPath);

  /**
   * Add source root to the module configuration.
   * {@link #addContentRoot(String)} should be called first
   *
   * @param sourceRootPath path to your source folder (relative to the first content root)
   * @return current builder
   */
  ModuleFixtureBuilder<T> addSourceRoot(String sourceRootPath);

  /**
   * Sets compiler output path.
   *
   * @param outputPath absolute path.
   */
  void setOutputPath(String outputPath);

  void setTestOutputPath(String outputPath);

  T getFixture();

  void addSourceContentRoot(String path);
}

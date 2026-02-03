// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.testFramework.builders;

import com.intellij.testFramework.fixtures.ModuleFixture;
import org.jetbrains.annotations.NotNull;

public interface ModuleFixtureBuilder<T extends ModuleFixture> {

  /**
   * Add content root to the module configuration.
   *
   * @param contentRootPath path to your content folder
   * @return current builder
   */
  @NotNull
  ModuleFixtureBuilder<T> addContentRoot(@NotNull String contentRootPath);

  /**
   * Add source root to the module configuration.
   * {@link #addContentRoot(String)} should be called first
   *
   * @param sourceRootPath path to your source folder (relative to the first content root)
   * @return current builder
   */
  @NotNull
  ModuleFixtureBuilder<T> addSourceRoot(@NotNull String sourceRootPath);

  /**
   * Sets compiler output path.
   *
   * @param outputPath absolute path.
   */
  void setOutputPath(@NotNull String outputPath);

  void setTestOutputPath(@NotNull String outputPath);

  @NotNull
  T getFixture();

  void addSourceContentRoot(@NotNull String path);
}

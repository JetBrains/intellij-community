// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Provides an ability to customize the paths where configuration and caches of IDE will be stored.
 * The name of the implementing class should be passed to the JVM command line via 'idea.paths.customizer' system property.
 */
@ApiStatus.Internal
public interface PathCustomizer {
  @Nullable CustomPaths customizePaths();

  final class CustomPaths {
    public CustomPaths(@Nullable String configPath, @Nullable String systemPath, @Nullable String pluginsPath, @Nullable String logDirPath,
                       @Nullable Path startupScriptDir) {
      this.configPath = configPath;
      this.systemPath = systemPath;
      this.pluginsPath = pluginsPath;
      this.logDirPath = logDirPath;
      this.startupScriptDir = startupScriptDir;
    }

    public final @Nullable String configPath;
    public final @Nullable String systemPath;
    public final @Nullable String pluginsPath;
    public final @Nullable String logDirPath;
    public final @Nullable Path startupScriptDir;
  }
}

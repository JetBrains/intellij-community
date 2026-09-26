// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Provides information about dependencies and classpath for a plugin (platform) module at runtime.
 * Unlike {@link RuntimeModuleDescriptor}, dependencies aren't resolved and may not be available.
 * <p>
 * After code that uses {@link RuntimeModuleDescriptor} migrates to use this interface instead and perform dependency resolution on the
 * plugin model side, this interface will become the primary way to access module information at runtime and {@link RuntimeModuleDescriptor}
 * will become deprecated.
  */
@ApiStatus.Internal
public interface RuntimeModuleHeader {
  @NotNull RuntimeModuleId getModuleId();

  /**
   * Returns IDs of direct dependencies of this module.
   */
  @NotNull List<@NotNull RuntimeModuleId> getDependencies();

  /**
   * Returns paths to classpath entries of this module, not including classpath entries of dependencies.
   */
  @NotNull List<@NotNull Path> getOwnClasspath();

  /**
   * Finds a file by the given {@code relativePath} under one of the {@link #getResourceRootPaths() resource roots} and opens it for
   * reading or return {@code null} if the file isn't found.
   */
  @Nullable InputStream readFile(@NotNull String relativePath) throws IOException;
}

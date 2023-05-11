// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Represents a module available at runtime. 
 * Currently, an instance of this interface corresponds to either production part of a module defined in IntelliJ project, or a library
 * used in IntelliJ project.
 */
@ApiStatus.NonExtendable
public interface RuntimeModuleDescriptor {
  @NotNull
  RuntimeModuleId getModuleId();

  /**
   * Returns list of this module's direct dependencies.
   */
  @NotNull
  List<RuntimeModuleDescriptor> getDependencies();

  /**
   * Returns list of resource roots (currently directories with *.class files or JAR archives) of this module.
   */
  @NotNull
  List<Path> getResourceRootPaths();

  /**
   * Finds a file by the given {@code relativePath} under one of the {@link #getResourceRootPaths() resource roots} and opens it for
   * reading or return {@code null} if the file isn't found.
   */
  @Nullable
  InputStream readFile(@NotNull String relativePath) throws IOException;

  /**
   * Returns paths to resource roots of this module and its dependencies (including transitive) which contain *.class files. 
   */
  @NotNull List<Path> getModuleClasspath();
}

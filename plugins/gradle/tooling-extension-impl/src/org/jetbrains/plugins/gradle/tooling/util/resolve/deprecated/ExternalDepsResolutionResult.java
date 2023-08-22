// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util.resolve.deprecated;

import org.jetbrains.plugins.gradle.model.ExternalDependency;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @deprecated use org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl
 */
@Deprecated
public class ExternalDepsResolutionResult {
  public static final ExternalDepsResolutionResult
    EMPTY = new ExternalDepsResolutionResult(Collections.emptySet(), Collections.emptySet());
  private final Collection<ExternalDependency> externalDeps;
  private final Collection<File> resolvedFiles;

  public ExternalDepsResolutionResult(Collection<ExternalDependency> deps, Collection<File> files) {
    externalDeps = deps;
    resolvedFiles = files;
  }

  public Collection<File> getResolvedFiles() {
    return resolvedFiles;
  }

  public Collection<ExternalDependency> getExternalDeps() {
    return externalDeps;
  }
}

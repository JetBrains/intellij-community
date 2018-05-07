// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.util.resolve;

import org.jetbrains.plugins.gradle.model.ExternalDependency;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class ExternalDepsResolutionResult {
  public static ExternalDepsResolutionResult
    EMPTY = new ExternalDepsResolutionResult(Collections.<ExternalDependency>emptySet(), Collections.<File>emptySet());
  private final Collection<ExternalDependency> externalDeps;
  private final Collection<File> resolvedFiles;

  public ExternalDepsResolutionResult() {
    externalDeps = new ArrayList<ExternalDependency>();
    resolvedFiles = new ArrayList<File>();
  }

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

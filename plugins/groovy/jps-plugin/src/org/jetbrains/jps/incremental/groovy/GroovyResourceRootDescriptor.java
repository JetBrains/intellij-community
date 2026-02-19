// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.groovy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.java.ResourceRootDescriptor;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.Set;

final class GroovyResourceRootDescriptor extends BuildRootDescriptor {
  private final CheckResourcesTarget myTarget;
  private final ResourceRootDescriptor myDescriptor;

  GroovyResourceRootDescriptor(ResourceRootDescriptor descriptor, CheckResourcesTarget target) {
    myDescriptor = descriptor;
    myTarget = target;
  }

  @Override
  public @NotNull CheckResourcesTarget getTarget() {
    return myTarget;
  }

  @Override
  public @NotNull FileFilter createFileFilter() {
    return myDescriptor.createFileFilter();
  }

  @Override
  public boolean isGenerated() {
    return myDescriptor.isGenerated();
  }

  @Override
  public String toString() {
    return myDescriptor.toString();
  }

  @Override
  public @NotNull String getRootId() {
    return myDescriptor.getRootId();
  }

  @Override
  public @NotNull File getRootFile() {
    return myDescriptor.getRootFile();
  }

  @Override
  public @NotNull Set<Path> getExcludedRoots() {
    return myDescriptor.getExcludedRoots();
  }
}

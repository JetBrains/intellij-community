// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.groovy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.java.ResourceRootDescriptor;

import java.io.File;
import java.io.FileFilter;
import java.util.Set;

class GroovyResourceRootDescriptor extends BuildRootDescriptor {
  private final CheckResourcesTarget myTarget;
  private final ResourceRootDescriptor myDescriptor;

  GroovyResourceRootDescriptor(ResourceRootDescriptor descriptor, CheckResourcesTarget target) {
    myDescriptor = descriptor;
    myTarget = target;
  }

  @NotNull
  @Override
  public CheckResourcesTarget getTarget() {
    return myTarget;
  }

  @Override
  @NotNull
  public FileFilter createFileFilter() {
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
  public boolean canUseFileCache() {
    return myDescriptor.canUseFileCache();
  }

  @Override
  public String getRootId() {
    return myDescriptor.getRootId();
  }

  @Override
  public File getRootFile() {
    return myDescriptor.getRootFile();
  }

  @NotNull
  @Override
  public Set<File> getExcludedRoots() {
    return myDescriptor.getExcludedRoots();
  }
}

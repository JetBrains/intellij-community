/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.groovy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.java.ResourceRootDescriptor;
import org.jetbrains.jps.cmdline.ProjectDescriptor;

import java.io.File;
import java.io.FileFilter;
import java.util.Set;

/**
 * @author peter
 */
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

  @NotNull
  public String getPackagePrefix() {
    return myDescriptor.getPackagePrefix();
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

  @Override
  public FileFilter createFileFilter(@NotNull ProjectDescriptor descriptor) {
    return myDescriptor.createFileFilter(descriptor);
  }

  @NotNull
  @Override
  public Set<File> getExcludedRoots() {
    return myDescriptor.getExcludedRoots();
  }
}

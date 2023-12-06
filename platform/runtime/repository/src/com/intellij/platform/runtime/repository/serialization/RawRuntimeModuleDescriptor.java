// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Describes raw data of a runtime module descriptor. This class is used in code which generates the module repository, if you need to 
 * get information about modules in IDE, use {@link com.intellij.platform.runtime.repository.RuntimeModuleDescriptor} instead.
 */
public final class RawRuntimeModuleDescriptor {
  private final String myId;
  private final List<String> myResourcePaths;
  private final List<String> myDependencies;

  public RawRuntimeModuleDescriptor(@NotNull String id, @NotNull List<String> resourcePaths, @NotNull List<String> dependencies) {
    myId = id;
    myResourcePaths = resourcePaths;
    myDependencies = dependencies;
  }

  public @NotNull String getId() {
    return myId;
  }

  public @NotNull List<String> getResourcePaths() {
    return myResourcePaths;
  }

  public @NotNull List<String> getDependencies() {
    return myDependencies;
  }

  @Override
  public String toString() {
    return "RawRuntimeModuleDescriptor{" +
           "id='" + myId + '\'' +
           ", resourcePaths=" + myResourcePaths +
           ", dependencies=" + myDependencies +
           '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RawRuntimeModuleDescriptor that = (RawRuntimeModuleDescriptor)o;

    if (!myId.equals(that.myId)) return false;
    if (!myResourcePaths.equals(that.myResourcePaths)) return false;
    if (!myDependencies.equals(that.myDependencies)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myId.hashCode();
    result = 31 * result + myResourcePaths.hashCode();
    result = 31 * result + myDependencies.hashCode();
    return result;
  }
}

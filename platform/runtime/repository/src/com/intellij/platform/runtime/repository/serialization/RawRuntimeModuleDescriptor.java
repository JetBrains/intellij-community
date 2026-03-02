// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization;

import com.intellij.platform.runtime.repository.RuntimeModuleId;
import org.jetbrains.annotations.NotNull;
import com.intellij.platform.runtime.repository.RuntimeModuleVisibility;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Describes raw data of a runtime module descriptor. This class is used in code which generates the module repository, if you need to 
 * get information about modules in IDE, use {@link com.intellij.platform.runtime.repository.RuntimeModuleDescriptor} instead.
 */
public final class RawRuntimeModuleDescriptor {
  private final RuntimeModuleId myId;
  private final RuntimeModuleVisibility myVisibility;
  private final List<String> myResourcePaths;
  private final List<RuntimeModuleId> myDependencies;

  /**
   * @deprecated use {@link #create(RuntimeModuleId, List, List)} instead
   */
  @Deprecated(forRemoval = true)
  public RawRuntimeModuleDescriptor(@NotNull String id, @NotNull List<String> resourcePaths, @NotNull List<String> dependencies) {
    myId = RuntimeModuleId.raw(id);
    myVisibility = RuntimeModuleVisibility.PUBLIC;
    myResourcePaths = resourcePaths;
    myDependencies = new ArrayList<>(dependencies.size());
    for (String dependency : dependencies) {
      myDependencies.add(RuntimeModuleId.raw(dependency));
    }
  }

  private RawRuntimeModuleDescriptor(@NotNull RuntimeModuleId id, @NotNull RuntimeModuleVisibility visibility,
                                     @NotNull List<String> resourcePaths, @NotNull List<RuntimeModuleId> dependencies) {
    myId = id;
    myVisibility = visibility;
    myResourcePaths = resourcePaths;
    myDependencies = dependencies;
  }

  public @NotNull RuntimeModuleId getModuleId() {
    return myId;
  }

  /**
   * @deprecated use {@link #getModuleId()} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull String getId() {
    return myId.getStringId();
  }

  public @NotNull List<String> getResourcePaths() {
    return myResourcePaths;
  }

  public @NotNull RuntimeModuleVisibility getVisibility() {
    return myVisibility;
  }

  /**
   * @deprecated use {@link #getDependencyIds()} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull List<String> getDependencies() {
    //noinspection SSBasedInspection
    return myDependencies.stream().map(RuntimeModuleId::getStringId).collect(Collectors.toList());
  }

  public @NotNull List<RuntimeModuleId> getDependencyIds() {
    return myDependencies;
  }

  @Override
  public String toString() {
    return "RawRuntimeModuleDescriptor{" +
           "id='" + myId.getPresentableName() + '\'' +
           ", visibility=" + myVisibility.name() +
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
    if (myVisibility != that.myVisibility) return false;
    if (!myResourcePaths.equals(that.myResourcePaths)) return false;
    if (!myDependencies.equals(that.myDependencies)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myId.hashCode();
    result = 31 * result + myVisibility.hashCode();
    result = 31 * result + myResourcePaths.hashCode();
    result = 31 * result + myDependencies.hashCode();
    return result;
  }

  public static @NotNull RawRuntimeModuleDescriptor create(@NotNull RuntimeModuleId id, @NotNull List<String> resourcePaths, @NotNull List<RuntimeModuleId> dependencies) {
    return new RawRuntimeModuleDescriptor(id, RuntimeModuleVisibility.PUBLIC, resourcePaths, dependencies);
  }

  public static @NotNull RawRuntimeModuleDescriptor create(@NotNull RuntimeModuleId id, @NotNull RuntimeModuleVisibility visibility, @NotNull List<String> resourcePaths, @NotNull List<RuntimeModuleId> dependencies) {
    return new RawRuntimeModuleDescriptor(id, visibility, resourcePaths, dependencies);
  }

  /**
   * @deprecated use {@link #create(RuntimeModuleId, List, List)} instead
   */
  @Deprecated(forRemoval = true)
  public static @NotNull RawRuntimeModuleDescriptor create(@NotNull String id, @NotNull List<String> resourcePaths, @NotNull List<String> dependencies) {
    return new RawRuntimeModuleDescriptor(id, resourcePaths, dependencies);
  }
}

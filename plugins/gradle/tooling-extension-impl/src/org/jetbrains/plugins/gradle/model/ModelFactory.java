// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author Vladislav.Soroka
 */
public final class ModelFactory {

  public static @NotNull ExternalDependency createCopy(@NotNull ExternalDependency dependency) {
    ExternalDependency newDep;
    if (dependency instanceof ExternalProjectDependency) {
      newDep = new DefaultExternalProjectDependency((ExternalProjectDependency)dependency);
    }
    else if (dependency instanceof ExternalLibraryDependency) {
      newDep = new DefaultExternalLibraryDependency((ExternalLibraryDependency)dependency);
    }
    else if (dependency instanceof FileCollectionDependency) {
      newDep = new DefaultFileCollectionDependency((FileCollectionDependency)dependency);
    }
    else if (dependency instanceof UnresolvedExternalDependency) {
      newDep = new DefaultUnresolvedExternalDependency((UnresolvedExternalDependency)dependency);
    }
    else {
      throw new AssertionError("unknown dependency object which implements: " + Arrays.toString(dependency.getClass().getInterfaces()));
    }
    return newDep;
  }

  public static @NotNull Collection<ExternalDependency> createCopy(@Nullable Collection<? extends ExternalDependency> dependencies) {
    if (dependencies == null) {
      // Collection can be modified outside by mutation methods
      return new ArrayList<>(0);
    }
    Collection<ExternalDependency> result = new ArrayList<>(dependencies.size());
    for (ExternalDependency dependency : dependencies) {
      result.add(createCopy(dependency));
    }
    return result;
  }
}

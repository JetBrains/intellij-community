// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.serialization.PropertyMapping;
import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ExternalDependency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import static com.intellij.util.containers.ContainerUtil.immutableList;

public final class AnnotationProcessingData {
  public static final Key<AnnotationProcessingData> KEY = Key.create(AnnotationProcessingData.class, ExternalSystemConstants.UNORDERED);
  public static final Key<AnnotationProcessorOutput> OUTPUT_KEY =
    Key.create(AnnotationProcessorOutput.class, ExternalSystemConstants.UNORDERED);

  private static final Interner<AnnotationProcessingData> ourInterner = Interner.createWeakInterner();

  private final Collection<String> arguments;
  private final Collection<ExternalDependency> dependencies;

  public static AnnotationProcessingData create(@NotNull Collection<String> arguments,
                                                @NotNull Collection<ExternalDependency> dependencies) {
    return ourInterner.intern(new AnnotationProcessingData(arguments, dependencies));
  }

  @PropertyMapping({"arguments", "dependencies"})
  private AnnotationProcessingData(@NotNull Collection<String> arguments, Collection<ExternalDependency> dependencies) {
    this.arguments = immutableList(new ArrayList<>(arguments));
    this.dependencies = immutableList(new ArrayList<>(dependencies == null ? new ArrayList<>() : dependencies));
  }

  /**
   * Annotation processor arguments
   * @return immutable collection of arguments
   */
  public Collection<String> getArguments() {
    return arguments;
  }

  /**
   * Annotation processor path
   * @return immutable collection of path elements
   */
  public Collection<ExternalDependency> getDependencies() {
    return dependencies;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AnnotationProcessingData data = (AnnotationProcessingData)o;

    if (!arguments.equals(data.arguments)) return false;
    if (!dependencies.equals(data.dependencies)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(arguments, dependencies);
  }

  public static class AnnotationProcessorOutput {
    private final String outputPath;
    private final boolean testSources;

    @PropertyMapping({"outputPath", "testSources"})
    public AnnotationProcessorOutput(@NotNull String path, boolean isTestSources) {
      outputPath = path;
      testSources = isTestSources;
    }

    @NotNull
    public String getOutputPath() {
      return outputPath;
    }

    public boolean isTestSources() {
      return testSources;
    }
  }
}

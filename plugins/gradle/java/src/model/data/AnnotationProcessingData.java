// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.serialization.PropertyMapping;
import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

public final class AnnotationProcessingData {
  public static final Key<AnnotationProcessingData> KEY = Key.create(AnnotationProcessingData.class, ExternalSystemConstants.UNORDERED);
  public static final Key<AnnotationProcessorOutput> OUTPUT_KEY =
    Key.create(AnnotationProcessorOutput.class, ExternalSystemConstants.UNORDERED);

  private static final Interner<AnnotationProcessingData> ourInterner = Interner.createWeakInterner();

  private final Collection<String> path;
  private final Collection<String> arguments;

  public static AnnotationProcessingData create(@NotNull Collection<String> path, @NotNull Collection<String> arguments) {
    return ourInterner.intern(new AnnotationProcessingData(path, arguments));
  }

  @PropertyMapping({"path", "arguments"})
  private AnnotationProcessingData(@NotNull Collection<String> path, @NotNull Collection<String> arguments) {
    this.path = List.copyOf(path);
    this.arguments = List.copyOf(arguments);
  }

  /**
   * Annotation processor arguments
   * @return immutable collection of arguments
   */
  @Unmodifiable
  public Collection<String> getArguments() {
    return arguments;
  }

  /**
   * Annotation processor path
   * @return immutable collection of path elements
   */
  @Unmodifiable
  public Collection<String> getPath() {
    return path;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AnnotationProcessingData data = (AnnotationProcessingData)o;

    if (!path.equals(data.path)) return false;
    if (!arguments.equals(data.arguments)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = path.hashCode();
    result = 31 * result + arguments.hashCode();
    return result;
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

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.util.BooleanBiFunction;
import org.jetbrains.plugins.gradle.tooling.util.GradleContainerUtil;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;

public final class DefaultExternalMultiLibraryDependency extends AbstractExternalDependency implements ExternalMultiLibraryDependency {
  private static final long serialVersionUID = 1L;
  private final Collection<File> files;
  private final Collection<File> sources;
  private final Collection<File> javadocs;

  public DefaultExternalMultiLibraryDependency() {
    files = new LinkedHashSet<>(0);
    sources = new LinkedHashSet<>(0);
    javadocs = new LinkedHashSet<>(0);
  }

  public DefaultExternalMultiLibraryDependency(ExternalLibraryDependency dependency) {
    super(dependency);
    files = new LinkedHashSet<>();
    sources = new LinkedHashSet<>();
    javadocs = new LinkedHashSet<>();

    addArtifactsFrom(dependency);
  }

  public void addArtifactsFrom(ExternalLibraryDependency dependency) {
    addIfNotNull(files, dependency.getFile());
    addIfNotNull(sources, dependency.getSource());
    addIfNotNull(javadocs, dependency.getJavadoc());
  }

  private static <T> void addIfNotNull(@NotNull Collection<T> targetCollection, @Nullable T file) {
    if (file != null) {
      targetCollection.add(file);
    }
  }

  @Override
  public @NotNull Collection<File> getFiles() {
    return files;
  }

  @Override
  public @NotNull Collection<File> getSources() {
    return sources;
  }

  @Override
  public @NotNull Collection<File> getJavadoc() {
    return javadocs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DefaultExternalMultiLibraryDependency)) return false;
    if (!super.equals(o)) return false;
    DefaultExternalMultiLibraryDependency that = (DefaultExternalMultiLibraryDependency)o;
    return GradleContainerUtil.match(files.iterator(), that.files.iterator(), new BooleanBiFunction<File, File>() {
      @Override
      public Boolean fun(File o1, File o2) {
        return Objects.equal(o1.getPath(), o2.getPath());
      }
    });
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), calcFilesPathsHashCode(files));
  }

  @Override
  public String toString() {
    return "library '" + files + '\'' + super.toString();
  }
}

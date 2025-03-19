// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.internal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ClasspathEntryModel;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.jetbrains.plugins.gradle.tooling.util.GradleContainerUtil.unmodifiablePathSet;

/**
 * @author Vladislav.Soroka
 */
public class ClasspathEntryModelImpl implements ClasspathEntryModel, Serializable {
  private final @NotNull Set<File> classes;
  private final @NotNull Set<File> sources;
  private final @NotNull Set<File> javadoc;

  public ClasspathEntryModelImpl(@NotNull Collection<File> classes, @NotNull Collection<File> sources, @NotNull Collection<File> javadoc) {
    this.classes = new LinkedHashSet<>(classes);
    this.sources = new LinkedHashSet<>(sources);
    this.javadoc = new LinkedHashSet<>(javadoc);
  }

  @Override
  public @NotNull Set<String> getClasses() {
    return unmodifiablePathSet(classes);
  }

  @Override
  public @NotNull Set<String> getSources() {
    return unmodifiablePathSet(sources);
  }

  @Override
  public @NotNull Set<String> getJavadoc() {
    return unmodifiablePathSet(javadoc);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ClasspathEntryModelImpl model = (ClasspathEntryModelImpl)o;

    if (!classes.equals(model.classes)) return false;
    if (!sources.equals(model.sources)) return false;
    if (!javadoc.equals(model.javadoc)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = classes.hashCode();
    result = 31 * result + sources.hashCode();
    result = 31 * result + javadoc.hashCode();
    return result;
  }
}

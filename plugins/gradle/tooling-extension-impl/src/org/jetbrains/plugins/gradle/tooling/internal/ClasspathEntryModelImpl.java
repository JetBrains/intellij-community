// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  private final Set<File> classes;
  @NotNull
  private final Set<File> sources;
  @NotNull
  private final Set<File> javadoc;

  public ClasspathEntryModelImpl(@NotNull Collection<File> classes, @NotNull Collection<File> sources, @NotNull Collection<File> javadoc) {
    this.classes = new LinkedHashSet<>(classes);
    this.sources = new LinkedHashSet<>(sources);
    this.javadoc = new LinkedHashSet<>(javadoc);
  }

  @NotNull
  @Override
  public Set<String> getClasses() {
    return unmodifiablePathSet(classes);
  }

  @NotNull
  @Override
  public Set<String> getSources() {
    return unmodifiablePathSet(sources);
  }

  @NotNull
  @Override
  public Set<String> getJavadoc() {
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

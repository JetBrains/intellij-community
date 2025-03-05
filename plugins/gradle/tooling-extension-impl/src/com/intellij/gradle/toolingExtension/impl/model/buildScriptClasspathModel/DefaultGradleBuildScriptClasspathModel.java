// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.buildScriptClasspathModel;

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ClasspathEntryModel;
import org.jetbrains.plugins.gradle.model.GradleBuildScriptClasspathModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public class DefaultGradleBuildScriptClasspathModel implements GradleBuildScriptClasspathModel {

  private final List<ClasspathEntryModel> myClasspathEntries;
  private @Nullable File gradleHomeDir;
  private String myGradleVersion;

  public DefaultGradleBuildScriptClasspathModel() {
    myClasspathEntries = new ArrayList<>(0);
  }

  @Override
  public DomainObjectSet<? extends ClasspathEntryModel> getClasspath() {
    return ImmutableDomainObjectSet.of(myClasspathEntries);
  }

  public void setGradleHomeDir(@Nullable File file) {
    gradleHomeDir = file;
  }

  @Override
  public @Nullable File getGradleHomeDir() {
    return gradleHomeDir;
  }

  public void add(@NotNull ClasspathEntryModel classpathEntryModel) {
    myClasspathEntries.add(classpathEntryModel);
  }

  public void setGradleVersion(@NotNull String gradleVersion) {
    myGradleVersion = gradleVersion;
  }

  @Override
  public @NotNull String getGradleVersion() {
    return myGradleVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DefaultGradleBuildScriptClasspathModel other = (DefaultGradleBuildScriptClasspathModel)o;

    if (!myClasspathEntries.equals(other.myClasspathEntries)) return false;
    if (gradleHomeDir == null && other.gradleHomeDir != null ||
        gradleHomeDir != null && (other.gradleHomeDir == null || !gradleHomeDir.getPath().equals(other.gradleHomeDir.getPath()))) {
      return false;
    }
    if (!Objects.equals(myGradleVersion, other.myGradleVersion)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myClasspathEntries.hashCode();
    result = 31 * result + (gradleHomeDir != null ? gradleHomeDir.hashCode() : 0);
    result = 31 * result + (myGradleVersion != null ? myGradleVersion.hashCode() : 0);
    return result;
  }
}

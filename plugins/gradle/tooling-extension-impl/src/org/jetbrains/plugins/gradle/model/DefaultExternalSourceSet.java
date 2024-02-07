// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public final class DefaultExternalSourceSet implements ExternalSourceSet {
  private static final long serialVersionUID = 2L;

  private String name;
  private boolean isPreview;
  private String sourceCompatibility;
  private String targetCompatibility;
  private String jdkInstallationPath;
  private Collection<File> artifacts;
  private final @NotNull LinkedHashSet<ExternalDependency> dependencies;
  private Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> sources;

  public DefaultExternalSourceSet() {
    sources = new HashMap<>(0);
    dependencies = new LinkedHashSet<>(0);
    artifacts = new ArrayList<>(0);
  }

  public DefaultExternalSourceSet(ExternalSourceSet sourceSet) {
    name = sourceSet.getName();
    isPreview = sourceSet.isPreview();
    sourceCompatibility = sourceSet.getSourceCompatibility();
    targetCompatibility = sourceSet.getTargetCompatibility();

    artifacts = sourceSet.getArtifacts() == null ? new ArrayList<>(0) : new ArrayList<>(sourceSet.getArtifacts());

    Set<? extends Map.Entry<? extends IExternalSystemSourceType, ? extends ExternalSourceDirectorySet>> entrySet = sourceSet.getSources().entrySet();
    sources = new HashMap<>(entrySet.size());
    for (Map.Entry<? extends IExternalSystemSourceType, ? extends ExternalSourceDirectorySet> entry : entrySet) {
      sources.put(ExternalSystemSourceType.from(entry.getKey()), new DefaultExternalSourceDirectorySet(entry.getValue()));
    }

    dependencies = new LinkedHashSet<>(sourceSet.getDependencies().size());
    for (ExternalDependency dependency : sourceSet.getDependencies()) {
      dependencies.add(ModelFactory.createCopy(dependency));
    }
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public boolean isPreview() {
    return isPreview;
  }

  public void setPreview(boolean preview) {
    isPreview = preview;
  }

  @Override
  public @Nullable String getSourceCompatibility() {
    return sourceCompatibility;
  }

  public void setSourceCompatibility(@Nullable String sourceCompatibility) {
    this.sourceCompatibility = sourceCompatibility;
  }

  @Override
  public @Nullable String getTargetCompatibility() {
    return targetCompatibility;
  }

  public void setTargetCompatibility(@Nullable String targetCompatibility) {
    this.targetCompatibility = targetCompatibility;
  }

  @Override
  public @Nullable String getJdkInstallationPath() {
    return jdkInstallationPath;
  }

  public void setJdkInstallationPath(@Nullable String jdkInstallationPath) {
    this.jdkInstallationPath = jdkInstallationPath;
  }

  @Override
  public Collection<File> getArtifacts() {
    return artifacts;
  }

  public void setArtifacts(Collection<File> artifacts) {
    this.artifacts = artifacts;
  }

  @Override
  public @NotNull Collection<ExternalDependency> getDependencies() {
    return dependencies;
  }

  @Override
  public @NotNull Map<? extends IExternalSystemSourceType, ? extends ExternalSourceDirectorySet> getSources() {
    return sources;
  }

  public void setSources(Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> sources) {
    this.sources = sources;
  }

  @Override
  public String toString() {
    return "sourceSet '" + name + '\'' ;
  }
}

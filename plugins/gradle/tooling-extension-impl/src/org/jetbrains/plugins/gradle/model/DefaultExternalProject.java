// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.DefaultGradleSourceSetModel;
import com.intellij.gradle.toolingExtension.impl.model.taskModel.DefaultGradleTaskModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * @author Vladislav.Soroka
 */
public final class DefaultExternalProject implements ExternalProject {

  private String id;
  private String path;
  private String identityPath;
  private String name;
  private String qName;
  private String description;
  private String group;
  private String version;
  private File projectDir;
  private File buildDir;
  private File buildFile;
  private String externalSystemId;

  private @NotNull TreeMap<String, DefaultExternalProject> childProjects;

  private @NotNull DefaultGradleSourceSetModel sourceSetModel;
  private @NotNull DefaultGradleTaskModel taskModel;

  public DefaultExternalProject() {
    childProjects = new TreeMap<>();
    taskModel = new DefaultGradleTaskModel();
    sourceSetModel = new DefaultGradleSourceSetModel();
  }

  @Override
  public @NotNull String getExternalSystemId() {
    return externalSystemId;
  }


  @Override
  public @NotNull String getId() {
    return id;
  }

  public void setId(@NotNull String id) {
    this.id = id;
  }

  @Override
  public @NotNull String getPath() {
    return path;
  }

  public void setPath(@NotNull String path) {
    this.path = path;
  }

  @Override
  public @NotNull String getIdentityPath() {
    return identityPath;
  }

  public void setIdentityPath(@NotNull String path) {
    this.identityPath = path;
  }

  public void setExternalSystemId(@NotNull String externalSystemId) {
    this.externalSystemId = externalSystemId;
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  public void setName(@NotNull String name) {
    this.name = name;
  }

  @Override
  public @NotNull String getQName() {
    return qName;
  }

  public void setQName(@NotNull String QName) {
    qName = QName;
  }


  @Override
  public @Nullable String getDescription() {
    return description;
  }

  public void setDescription(@Nullable String description) {
    this.description = description;
  }

  @Override
  public @NotNull String getGroup() {
    return group;
  }

  public void setGroup(@NotNull String group) {
    this.group = group;
  }

  @Override
  public @NotNull String getVersion() {
    return version;
  }

  public void setVersion(@NotNull String version) {
    this.version = version;
  }

  @Override
  public @Nullable String getSourceCompatibility() {
    return sourceSetModel.getSourceCompatibility();
  }

  @Override
  public @Nullable String getTargetCompatibility() {
    return sourceSetModel.getTargetCompatibility();
  }

  @Override
  public @NotNull Map<String, DefaultExternalProject> getChildProjects() {
    return childProjects;
  }

  public void addChildProject(@NotNull DefaultExternalProject childProject) {
    childProjects.put(childProject.getName(), childProject);
  }

  public void setChildProjects(@NotNull Map<String, DefaultExternalProject> childProjects) {
    if (childProjects instanceof TreeMap) {
      this.childProjects = (TreeMap<String, DefaultExternalProject>)childProjects;
    }
    else {
      this.childProjects = new TreeMap<>(childProjects);
    }
  }

  @Override
  public @NotNull File getProjectDir() {
    return projectDir;
  }

  public void setProjectDir(@NotNull File projectDir) {
    this.projectDir = projectDir;
  }

  @Override
  public @NotNull File getBuildDir() {
    return buildDir;
  }

  public void setBuildDir(@NotNull File buildDir) {
    this.buildDir = buildDir;
  }

  @Override
  public @Nullable File getBuildFile() {
    return buildFile;
  }

  public void setBuildFile(@Nullable File buildFile) {
    this.buildFile = buildFile;
  }

  @Override
  public @NotNull Map<String, ? extends ExternalTask> getTasks() {
    return taskModel.getTasks();
  }

  @Override
  public @NotNull Map<String, DefaultExternalSourceSet> getSourceSets() {
    return sourceSetModel.getSourceSets();
  }

  @Override
  public @NotNull List<File> getArtifacts() {
    return sourceSetModel.getTaskArtifacts();
  }

  @Override
  public @NotNull Map<String, Set<File>> getArtifactsByConfiguration() {
    return sourceSetModel.getConfigurationArtifacts();
  }

  @Override
  public String toString() {
    return "project '" + id + "'";
  }

  @Override
  public @NotNull DefaultGradleSourceSetModel getSourceSetModel() {
    return sourceSetModel;
  }

  public void setSourceSetModel(@NotNull DefaultGradleSourceSetModel sourceSetModel) {
    this.sourceSetModel = sourceSetModel;
  }

  @Override
  public @NotNull GradleTaskModel getTaskModel() {
    return taskModel;
  }

  public void setTaskModel(@NotNull DefaultGradleTaskModel taskModel) {
    this.taskModel = taskModel;
  }
}

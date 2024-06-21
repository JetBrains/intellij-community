// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.DefaultGradleSourceSetModel;
import com.intellij.gradle.toolingExtension.impl.model.taskModel.DefaultGradleTaskModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public final class DefaultExternalProject implements ExternalProject {
  @NotNull
  private String id;

  @NotNull
  private String path;

  @NotNull
  private String identityPath;

  @NotNull
  private String name;
  @NotNull
  private String qName;
  @Nullable
  private String description;
  @NotNull
  private String group;
  @NotNull
  private String version;
  @NotNull
  private TreeMap<String, DefaultExternalProject> childProjects;
  @NotNull
  private File projectDir;
  @NotNull
  private File buildDir;
  @Nullable
  private File buildFile;
  @NotNull
  private String externalSystemId;

  private @NotNull DefaultGradleSourceSetModel sourceSetModel;
  private @NotNull DefaultGradleTaskModel taskModel;

  public DefaultExternalProject() {
    childProjects = new TreeMap<>();
    taskModel = new DefaultGradleTaskModel();
    sourceSetModel = new DefaultGradleSourceSetModel();
  }

  @NotNull
  @Override
  public String getExternalSystemId() {
    return externalSystemId;
  }

  @NotNull
  @Override
  public String getId() {
    return id;
  }

  public void setId(@NotNull String id) {
    this.id = id;
  }

  @NotNull
  @Override
  public String getPath() {
    return path;
  }

  public void setPath(@NotNull String path) {
    this.path = path;
  }

  @NotNull
  @Override
  public String getIdentityPath() {
    return identityPath;
  }

  public void setIdentityPath(@NotNull String path) {
    this.identityPath = path;
  }

  public void setExternalSystemId(@NotNull String externalSystemId) {
    this.externalSystemId = externalSystemId;
  }

  @NotNull
  @Override
  public String getName() {
    return name;
  }

  public void setName(@NotNull String name) {
    this.name = name;
  }

  @NotNull
  @Override
  public String getQName() {
    return qName;
  }

  public void setQName(@NotNull String QName) {
    qName = QName;
  }

  @Nullable
  @Override
  public String getDescription() {
    return description;
  }

  public void setDescription(@Nullable String description) {
    this.description = description;
  }

  @NotNull
  @Override
  public String getGroup() {
    return group;
  }

  public void setGroup(@NotNull String group) {
    this.group = group;
  }

  @NotNull
  @Override
  public String getVersion() {
    return version;
  }

  public void setVersion(@NotNull String version) {
    this.version = version;
  }

  @Override
  @Nullable
  public String getSourceCompatibility() {
    return sourceSetModel.getSourceCompatibility();
  }

  @Override
  @Nullable
  public String getTargetCompatibility() {
    return sourceSetModel.getTargetCompatibility();
  }

  @NotNull
  @Override
  public Map<String, DefaultExternalProject> getChildProjects() {
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

  @NotNull
  @Override
  public File getProjectDir() {
    return projectDir;
  }

  public void setProjectDir(@NotNull File projectDir) {
    this.projectDir = projectDir;
  }

  @NotNull
  @Override
  public File getBuildDir() {
    return buildDir;
  }

  public void setBuildDir(@NotNull File buildDir) {
    this.buildDir = buildDir;
  }

  @Nullable
  @Override
  public File getBuildFile() {
    return buildFile;
  }

  public void setBuildFile(@Nullable File buildFile) {
    this.buildFile = buildFile;
  }

  @NotNull
  @Override
  public Map<String, ? extends ExternalTask> getTasks() {
    return taskModel.getTasks();
  }

  @NotNull
  @Override
  public Map<String, DefaultExternalSourceSet> getSourceSets() {
    return sourceSetModel.getSourceSets();
  }

  public void setSourceSets(@NotNull Map<String, DefaultExternalSourceSet> sourceSets) {
    sourceSetModel.setSourceSets(sourceSets);
  }

  @NotNull
  @Override
  public List<File> getArtifacts() {
    return sourceSetModel.getTaskArtifacts();
  }

  @NotNull
  @Override
  public Map<String, Set<File>> getArtifactsByConfiguration() {
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

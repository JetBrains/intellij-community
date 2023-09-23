// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.DefaultGradleSourceSetModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public final class DefaultExternalProject implements ExternalProject, ExternalProjectPreview {
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
  private Map<String, DefaultExternalTask> tasks;
  @NotNull
  private String externalSystemId;
  @NotNull
  private DefaultGradleSourceSetModel sourceSetModel;

  public DefaultExternalProject() {
    childProjects = new TreeMap<>();
    tasks = new HashMap<>(0);
    sourceSetModel = new DefaultGradleSourceSetModel();
  }

  public DefaultExternalProject(@NotNull ExternalProject externalProject) {
    id = externalProject.getId();
    path = externalProject.getPath();
    identityPath = externalProject.getIdentityPath();
    name = externalProject.getName();
    qName = externalProject.getQName();
    version = externalProject.getVersion();
    group = externalProject.getGroup();
    description = externalProject.getDescription();
    projectDir = externalProject.getProjectDir();
    buildDir = externalProject.getBuildDir();
    buildFile = externalProject.getBuildFile();
    externalSystemId = externalProject.getExternalSystemId();

    Map<String, ? extends ExternalProject> externalProjectChildProjects = externalProject.getChildProjects();
    childProjects = new TreeMap<>();
    for (Map.Entry<String, ? extends ExternalProject> entry : externalProjectChildProjects.entrySet()) {
      childProjects.put(entry.getKey(), new DefaultExternalProject(entry.getValue()));
    }

    Map<String, ? extends ExternalTask> externalProjectTasks = externalProject.getTasks();
    tasks = new HashMap<>(externalProjectTasks.size());
    for (Map.Entry<String, ? extends ExternalTask> entry : externalProjectTasks.entrySet()) {
      this.tasks.put(entry.getKey(), new DefaultExternalTask(entry.getValue()));
    }

    sourceSetModel = new DefaultGradleSourceSetModel(externalProject.getSourceSetModel());
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

  public void setSourceCompatibility(@Nullable String sourceCompatibility) {
    sourceSetModel.setSourceCompatibility(sourceCompatibility);
  }

  @Override
  @Nullable
  public String getTargetCompatibility() {
    return sourceSetModel.getTargetCompatibility();
  }

  public void setTargetCompatibility(@Nullable String targetCompatibility) {
    sourceSetModel.setTargetCompatibility(targetCompatibility);
  }

  @NotNull
  @Override
  public Map<String, DefaultExternalProject> getChildProjects() {
    return childProjects;
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
    return tasks;
  }

  public void setTasks(@NotNull Map<String, DefaultExternalTask> tasks) {
    this.tasks = tasks;
  }

  @Override
  public @NotNull DefaultGradleSourceSetModel getSourceSetModel() {
    return sourceSetModel;
  }

  public void setSourceSetModel(@NotNull DefaultGradleSourceSetModel sourceSetModel) {
    this.sourceSetModel = sourceSetModel;
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

  public void setArtifacts(@NotNull List<File> artifacts) {
    sourceSetModel.setTaskArtifacts(artifacts);
  }

  public void setArtifactsByConfiguration(@NotNull Map<String, Set<File>> artifactsByConfiguration) {
    sourceSetModel.setConfigurationArtifacts(artifactsByConfiguration);
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
}

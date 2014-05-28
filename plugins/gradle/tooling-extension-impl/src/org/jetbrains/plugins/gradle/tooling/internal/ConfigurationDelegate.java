/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.tooling.internal;

import groovy.lang.Closure;
import org.gradle.api.artifacts.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskDependency;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 5/28/2014
 */
public class ConfigurationDelegate implements Configuration {
  @NotNull
  private final Configuration delegate;

  public ConfigurationDelegate(@NotNull Configuration configuration) {
    delegate = configuration;
  }

  @Override
  public ResolutionStrategy getResolutionStrategy() {
    return delegate.getResolutionStrategy();
  }

  @Override
  public Configuration resolutionStrategy(Closure closure) {
    return delegate.resolutionStrategy(closure);
  }

  @Override
  public State getState() {
    return delegate.getState();
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public boolean isVisible() {
    return delegate.isVisible();
  }

  @Override
  public Configuration setVisible(boolean visible) {
    return delegate.setVisible(visible);
  }

  @Override
  public Set<Configuration> getExtendsFrom() {
    return delegate.getExtendsFrom();
  }

  @Override
  public Configuration setExtendsFrom(Set<Configuration> superConfigs) {
    return delegate.setExtendsFrom(superConfigs);
  }

  @Override
  public Configuration extendsFrom(Configuration... superConfigs) {
    return delegate.extendsFrom(superConfigs);
  }

  @Override
  public boolean isTransitive() {
    return delegate.isTransitive();
  }

  @Override
  public Configuration setTransitive(boolean t) {
    return delegate.setTransitive(t);
  }

  @Override
  public String getDescription() {
    return delegate.getDescription();
  }

  @Override
  public Configuration setDescription(String description) {
    return delegate.setDescription(description);
  }

  @Override
  public Set<Configuration> getHierarchy() {
    return delegate.getHierarchy();
  }

  @Override
  public Set<File> resolve() {
    return delegate.resolve();
  }

  @Override
  public Set<File> files(Closure dependencySpecClosure) {
    return delegate.files(dependencySpecClosure);
  }

  @Override
  public Set<File> files(Spec<? super Dependency> dependencySpec) {
    return delegate.files(dependencySpec);
  }

  @Override
  public Set<File> files(Dependency... dependencies) {
    return delegate.files(dependencies);
  }

  @Override
  public FileCollection fileCollection(Spec<? super Dependency> dependencySpec) {
    return delegate.fileCollection(dependencySpec);
  }

  @Override
  public FileCollection fileCollection(Closure dependencySpecClosure) {
    return delegate.fileCollection(dependencySpecClosure);
  }

  @Override
  public FileCollection fileCollection(Dependency... dependencies) {
    return delegate.fileCollection(dependencies);
  }

  @Override
  public ResolvedConfiguration getResolvedConfiguration() {
    return delegate.getResolvedConfiguration();
  }

  @Override
  public String getUploadTaskName() {
    return delegate.getUploadTaskName();
  }

  @Override
  public TaskDependency getBuildDependencies() {
    return delegate.getBuildDependencies();
  }

  @Override
  public TaskDependency getTaskDependencyFromProjectDependency(boolean useDependedOn, String taskName) {
    return delegate.getTaskDependencyFromProjectDependency(useDependedOn, taskName);
  }

  @Override
  public DependencySet getDependencies() {
    return delegate.getDependencies();
  }

  @Override
  public DependencySet getAllDependencies() {
    return delegate.getAllDependencies();
  }

  @Override
  public PublishArtifactSet getArtifacts() {
    return delegate.getArtifacts();
  }

  @Override
  public PublishArtifactSet getAllArtifacts() {
    return delegate.getAllArtifacts();
  }

  @Override
  public Set<ExcludeRule> getExcludeRules() {
    return delegate.getExcludeRules();
  }

  @Override
  public Configuration exclude(Map<String, String> excludeProperties) {
    return delegate.exclude(excludeProperties);
  }

  @Override
  public Set<Configuration> getAll() {
    return delegate.getAll();
  }

  @Override
  public ResolvableDependencies getIncoming() {
    return delegate.getIncoming();
  }

  @Override
  public Configuration copy() {
    return delegate.copy();
  }

  @Override
  public Configuration copyRecursive() {
    return delegate.copyRecursive();
  }

  @Override
  public Configuration copy(Spec<? super Dependency> dependencySpec) {
    return delegate.copy(dependencySpec);
  }

  @Override
  public Configuration copyRecursive(Spec<? super Dependency> dependencySpec) {
    return delegate.copyRecursive(dependencySpec);
  }

  @Override
  public Configuration copy(Closure dependencySpec) {
    return delegate.copy(dependencySpec);
  }

  @Override
  public Configuration copyRecursive(Closure dependencySpec) {
    return delegate.copyRecursive(dependencySpec);
  }

  @Override
  public File getSingleFile() throws IllegalStateException {
    return delegate.getSingleFile();
  }

  @Override
  public Set<File> getFiles() {
    return delegate.getFiles();
  }

  @Override
  public boolean contains(File file) {
    return delegate.contains(file);
  }

  @Override
  public String getAsPath() {
    return delegate.getAsPath();
  }

  @Override
  public FileCollection plus(FileCollection collection) {
    return delegate.plus(collection);
  }

  @Override
  public FileCollection minus(FileCollection collection) {
    return delegate.minus(collection);
  }

  @Override
  public FileCollection filter(Closure filterClosure) {
    return delegate.filter(filterClosure);
  }

  @Override
  public FileCollection filter(Spec<? super File> filterSpec) {
    return delegate.filter(filterSpec);
  }

  @Override
  public Object asType(Class<?> type) throws UnsupportedOperationException {
    return delegate.asType(type);
  }

  @Override
  public FileCollection add(FileCollection collection) throws UnsupportedOperationException {
    return delegate.add(collection);
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public FileCollection stopExecutionIfEmpty() throws StopExecutionException {
    return delegate.stopExecutionIfEmpty();
  }

  @Override
  public FileTree getAsFileTree() {
    return delegate.getAsFileTree();
  }

  @Override
  public void addToAntBuilder(Object builder, String nodeName, AntType type) {
    delegate.addToAntBuilder(builder, nodeName, type);
  }

  @Override
  public Object addToAntBuilder(Object builder, String nodeName) {
    return delegate.addToAntBuilder(builder, nodeName);
  }

  @Override
  public Iterator<File> iterator() {
    return delegate.iterator();
  }
}

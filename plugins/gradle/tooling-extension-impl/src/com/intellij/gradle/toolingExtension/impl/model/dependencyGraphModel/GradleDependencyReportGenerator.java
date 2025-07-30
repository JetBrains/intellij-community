// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyGraphModel;

import com.intellij.openapi.externalSystem.model.project.dependencies.*;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Describable;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.*;
import org.gradle.api.file.FileCollection;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class GradleDependencyReportGenerator {

  private final AtomicLong nextId = new AtomicLong();

  public DependencyScopeNode buildDependencyGraph(
    @NotNull Configuration configuration,
    @NotNull Project project
  ) {
    IdGenerator idGenerator = new IdGenerator(nextId);
    ResolutionResult resolutionResult = configuration.getIncoming().getResolutionResult();
    ResolvedComponentResult root = resolutionResult.getRoot();
    String configurationName = configuration.getName();
    long id = idGenerator.getId(root.getId());
    String scopeDisplayName = "project " + project.getPath() + " (" + configurationName + ")";
    DependencyScopeNode node = new DependencyScopeNode(id, configurationName, scopeDisplayName, configuration.getDescription());
    node.setResolutionState(ResolutionState.RESOLVED);
    for (Dependency dependency : configuration.getAllDependencies()) {
      if (dependency instanceof FileCollectionDependency) {
        FileCollection fileCollection = ((FileCollectionDependency)dependency).getFiles();
        if (fileCollection instanceof Configuration) continue;
        Set<File> files = fileCollection.getFiles();
        if (files.isEmpty()) continue;

        String displayName = null;
        if (fileCollection instanceof Describable) {
          displayName = ((Describable)fileCollection).getDisplayName();
        }
        else {
          String string = fileCollection.toString();
          if (!"file collection".equals(string)) {
            displayName = string;
          }
        }

        if (displayName != null) {
          long fileDepId = idGenerator.getId(displayName);
          node.getDependencies().add(new FileCollectionDependencyNodeImpl(fileDepId, displayName, fileCollection.getAsPath()));
        }
        else {
          for (File file : files) {
            long fileDepId = idGenerator.getId(file.getPath());
            node.getDependencies().add(new FileCollectionDependencyNodeImpl(fileDepId, file.getName(), file.getPath()));
          }
        }
      }
    }


    Map<Object, DependencyNode> added = new LinkedHashMap<>();

    for (DependencyResult child : root.getDependencies()) {
      node.getDependencies().add(buildDependencyNode(child, added, idGenerator));
    }

    return node;
  }

  private static DependencyNode buildDependencyNode(
    DependencyResult dependency,
    Map<Object, DependencyNode> added,
    IdGenerator idGenerator
  ) {
    if (dependency instanceof ResolvedDependencyResult) {
      return buildResolvedDependencyNode((ResolvedDependencyResult)dependency, added, idGenerator);
    }
    else if (dependency instanceof UnresolvedDependencyResult) {
      return buildUnresolvedDependencyNode((UnresolvedDependencyResult)dependency, added, idGenerator);
    }
    else {
      throw new IllegalStateException("Unsupported dependency result type: " + dependency);
    }
  }

  private static @NotNull DependencyNode buildResolvedDependencyNode(
    @NotNull ResolvedDependencyResult dependency,
    @NotNull Map<Object, DependencyNode> added,
    @NotNull IdGenerator idGenerator
  ) {
    ResolvedComponentResult resolvedComponent = dependency.getSelected();
    ComponentIdentifier componentId = resolvedComponent.getId();

    long id = idGenerator.getId(componentId);
    if (added.containsKey(id)) {
      return new ReferenceNode(id);
    }

    AbstractDependencyNode node;
    if (componentId instanceof ProjectComponentIdentifier) {
      ProjectComponentIdentifier projectId = (ProjectComponentIdentifier)componentId;
      String projectName = projectId.getProjectName();
      String projectPath = projectId.getProjectPath();
      node = new ProjectDependencyNodeImpl(id, projectName, projectPath);
    }
    else if (componentId instanceof ModuleComponentIdentifier) {
      ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier)componentId;
      node = new ArtifactDependencyNodeImpl(id, moduleId.getGroup(), moduleId.getModule(), moduleId.getVersion());
    }
    else {
      node = new UnknownDependencyNode(id, componentId.getDisplayName());
    }

    node.setResolutionState(ResolutionState.RESOLVED);
    ComponentSelectionReason selectionReason = resolvedComponent.getSelectionReason();
    if (DefaultGroovyMethods.asBoolean(DefaultGroovyMethods.hasProperty(selectionReason, "descriptions"))) {
      List<? extends ComponentSelectionDescriptor> descriptions = selectionReason.getDescriptions();
      if (!descriptions.isEmpty()) {
        node.setSelectionReason(DefaultGroovyMethods.last(descriptions).getDescription());
      }
    }

    added.put(id, node);

    for (DependencyResult child : resolvedComponent.getDependencies()) {
      node.getDependencies().add(buildDependencyNode(child, added, idGenerator));
    }

    return node;
  }

  private static @NotNull DependencyNode buildUnresolvedDependencyNode(
    @NotNull UnresolvedDependencyResult dependency,
    @NotNull Map<Object, DependencyNode> added,
    @NotNull IdGenerator idGenerator
  ) {
    ComponentSelector attempted = dependency.getAttempted();

    long id = idGenerator.getId(attempted);
    if (added.containsKey(id)) {
      return new ReferenceNode(id);
    }

    UnknownDependencyNode node = new UnknownDependencyNode(id, attempted.getDisplayName());
    node.setResolutionState(ResolutionState.UNRESOLVED);

    added.put(id, node);

    return node;
  }

  private static class IdGenerator {

    private final HashMap<Object, Long> idMap = new HashMap<>();
    private final AtomicLong nextId;

    private IdGenerator(AtomicLong nextId) {
      this.nextId = nextId;
    }

    public long getId(Object key) {
      long newId = nextId.incrementAndGet();
      Long existingId = idMap.putIfAbsent(key, newId);
      if (existingId != null) {
        return existingId;
      }

      return newId;
    }
  }
}

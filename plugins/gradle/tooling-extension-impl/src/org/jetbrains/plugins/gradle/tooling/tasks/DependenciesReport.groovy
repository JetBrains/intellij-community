// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.tasks

import com.google.gson.GsonBuilder
import com.intellij.openapi.externalSystem.model.project.dependencies.*
import gnu.trove.THashMap
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.Describable
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.*
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GradleVersion

import java.util.concurrent.atomic.AtomicLong

import static com.intellij.openapi.externalSystem.model.project.dependencies.ResolutionState.RESOLVED
import static com.intellij.openapi.externalSystem.model.project.dependencies.ResolutionState.UNRESOLVED

@CompileStatic
class DependenciesReport extends DefaultTask {

  @Input
  List<String> configurations = []
  @OutputFile
  File outputFile

  @TaskAction
  void generate() {
    def generator = new ReportGenerator()
    Collection<Configuration> configurationList
    if (configurations.isEmpty()) {
      configurationList = project.configurations
    }
    else {
      configurationList = new ArrayList<>()
      for (configurationName in configurations) {
        def configuration = project.configurations.findByName(configurationName)
        if (configuration != null) {
          configurationList.add(configuration)
        }
      }
    }

    def projectNameFunction = new ProjectNameFunction()
    List<DependencyScopeNode> graph = []
    for (configuration in configurationList) {
      if (!configuration.isCanBeResolved()) continue
      graph.add(generator.doBuildDependenciesGraph(configuration, project, projectNameFunction))
    }
    outputFile.parentFile.mkdirs()
    outputFile.text = new GsonBuilder().create().toJson(graph)
  }

  static class ReportGenerator {
    private static final boolean IS_51_OR_NEWER = GradleVersion.current() >= GradleVersion.version("5.1")

    private AtomicLong nextId = new AtomicLong()

    DependencyScopeNode buildDependenciesGraph(Configuration configuration, Project project) {
      return doBuildDependenciesGraph(configuration, project, new ProjectNameFunction())
    }

    private DependencyScopeNode doBuildDependenciesGraph(Configuration configuration,
                                                         Project project,
                                                         ProjectNameFunction projectNameFunction) {
      if (!project.configurations.contains(configuration)) {
        throw new IllegalArgumentException("configurations of the project should be used")
      }
      IdGenerator idGenerator = new IdGenerator(nextId)
      ResolutionResult resolutionResult = configuration.incoming.resolutionResult
      ResolvedComponentResult root = resolutionResult.root
      String configurationName = configuration.name
      long id = idGenerator.getId(getId(root))
      String scopeDisplayName = "project " + project.path + " (" + configurationName + ")"
      DependencyScopeNode node = new DependencyScopeNode(id, configurationName, scopeDisplayName, configuration.description)
      node.setResolutionState(RESOLVED)
      for (Dependency dependency : configuration.allDependencies) {
        if (dependency instanceof FileCollectionDependency) {
          FileCollection fileCollection = dependency.files
          if (fileCollection instanceof Configuration) continue
          def files = fileCollection.files
          if (files.empty) continue

          String displayName = null
          if (fileCollection instanceof Describable) {
            displayName = fileCollection.getDisplayName()
          }
          else {
            def string = fileCollection.toString()
            if ("file collection" != string) {
              displayName = string
            }
          }

          if (displayName != null) {
            long fileDepId = idGenerator.getId(displayName)
            node.dependencies.add(new FileCollectionDependencyNodeImpl(fileDepId, displayName, fileCollection.asPath))
          }
          else {
            for (File file : files) {
              long fileDepId = idGenerator.getId(file.path)
              node.dependencies.add(new FileCollectionDependencyNodeImpl(fileDepId, file.name, file.path))
            }
          }
        }
      }

      Map<Object, DependencyNode> added = [:]

      def iterator = root.dependencies.iterator()
      while (iterator.hasNext()) {
        DependencyResult child = iterator.next()
        node.dependencies.add(toNode(child, added, idGenerator, projectNameFunction))
      }
      return node
    }

    static private DependencyNode toNode(DependencyResult dependency,
                                         Map<Object, DependencyNode> added,
                                         IdGenerator idGenerator,
                                         ProjectNameFunction projectNameFunction) {

      def selectedOrAttempted = dependency instanceof ResolvedDependencyResult
        ? getId(dependency.selected)
        : (dependency as UnresolvedDependencyResult).attempted
      long id = idGenerator.getId(selectedOrAttempted)
      DependencyNode alreadySeenNode = added.get(id)
      if (alreadySeenNode != null) {
        return new ReferenceNode(id)
      }

      AbstractDependencyNode node
      if (dependency instanceof ResolvedDependencyResult) {
        def componentId = getId(dependency.selected)
        if (componentId instanceof ProjectComponentIdentifier) {
          node = new ProjectDependencyNodeImpl(id, projectNameFunction.fun(componentId))
        }
        else if (componentId instanceof ModuleComponentIdentifier) {
          node = new ArtifactDependencyNodeImpl(id, componentId.getGroup(), componentId.getModule(), componentId.getVersion())
        }
        else {
          node = new UnknownDependencyNode(id, componentId.displayName)
        }
        node.resolutionState = RESOLVED
        if (!dependency.selected.selectionReason.descriptions.isEmpty()) {
          node.selectionReason = dependency.selected.selectionReason.descriptions.last().description
        }
        added.put(id, node)
        def iterator = dependency.selected.dependencies.iterator()
        while (iterator.hasNext()) {
          DependencyResult child = iterator.next()
          node.dependencies.add(toNode(child, added, idGenerator, projectNameFunction))
        }
      } else if (dependency instanceof UnresolvedDependencyResult) {
        node = new UnknownDependencyNode(id, dependency.attempted.displayName)
        node.resolutionState = UNRESOLVED
        added.put(id, node)
      } else {
        throw new IllegalStateException("Unsupported dependency result type: $dependency")
      }

      return node
    }

    /**
     * The new version of Gradle we compile against has the getId method on a super-interface that didn't exist before Gradle 5.1
     * Use dynamic access to work around this.
     */
    private static ComponentIdentifier getId(ResolvedComponentResult result) {
      if (IS_51_OR_NEWER) {
        result.id
      } else {
        getIdDynamically(result)
      }
    }

    @CompileDynamic
    private static ComponentIdentifier getIdDynamically(ResolvedComponentResult result) {
      result.getId()
    }

    private static class IdGenerator {
      private THashMap<Object, Long> idMap = new THashMap<>()
      private AtomicLong nextId

      IdGenerator(AtomicLong nextId) {
        this.nextId = nextId
      }

      long getId(Object key) {
        def newId = nextId.incrementAndGet()
        def existingId = idMap.putIfAbsent(key, newId)
        if (existingId != null) {
          return existingId
        }
        return newId
      }
    }
  }

  static class ProjectNameFunction {
    def is45OrNewer = GradleVersion.current() >= GradleVersion.version("4.5")

    String fun(ProjectComponentIdentifier identifier) {
      return is45OrNewer ? identifier.projectName : identifier.projectPath
    }
  }

}
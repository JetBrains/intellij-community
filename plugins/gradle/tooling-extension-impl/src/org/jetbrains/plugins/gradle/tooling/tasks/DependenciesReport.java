// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.tasks;

import com.google.gson.GsonBuilder;
import com.intellij.openapi.externalSystem.model.project.dependencies.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.Describable;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class DependenciesReport extends DefaultTask {
  private static final boolean is45OrNewer = GradleVersion.current().compareTo(GradleVersion.version("4.5")) >= 0;

  @TaskAction
  public void generate() throws IOException {
    ReportGenerator generator = new ReportGenerator();
    Collection<Configuration> configurationList;
    if (configurations.isEmpty()) {
      configurationList = getProject().getConfigurations();
    }
    else {
      configurationList = new ArrayList<>();
      for (String configurationName : configurations) {
        Configuration configuration = getProject().getConfigurations().findByName(configurationName);
        if (configuration != null) {
          configurationList.add(configuration);
        }
      }
    }

    List<DependencyScopeNode> graph = new ArrayList<>();
    for (Configuration configuration : configurationList) {
      if (!configuration.isCanBeResolved()) continue;
      graph.add(generator.doBuildDependenciesGraph(configuration, getProject()));
    }

    Path outputFilePath = outputFile.toPath();
    Files.createDirectories(outputFilePath.getParent());
    Files.write(outputFilePath, new GsonBuilder().create().toJson(graph).getBytes(StandardCharsets.UTF_8));
  }

  public List<String> getConfigurations() {
    return configurations;
  }

  public void setConfigurations(List<String> configurations) {
    this.configurations = configurations;
  }

  public File getOutputFile() {
    return outputFile;
  }

  public void setOutputFile(File outputFile) {
    this.outputFile = outputFile;
  }

  @Input private List<String> configurations = new ArrayList<>();
  @OutputFile private File outputFile;

  public static class ReportGenerator {
    public DependencyScopeNode buildDependenciesGraph(Configuration configuration, Project project) {
      return doBuildDependenciesGraph(configuration, project);
    }

    private DependencyScopeNode doBuildDependenciesGraph(Configuration configuration,
                                                         Project project) {
      if (!project.getConfigurations().contains(configuration)) {
        throw new IllegalArgumentException("configurations of the project should be used");
      }

      IdGenerator idGenerator = new IdGenerator(nextId);
      ResolutionResult resolutionResult = configuration.getIncoming().getResolutionResult();
      ResolvedComponentResult root = resolutionResult.getRoot();
      String configurationName = configuration.getName();
      long id = idGenerator.getId(getId(root));
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
        node.getDependencies().add(toNode(child, added, idGenerator));
      }

      return node;
    }

    private static DependencyNode toNode(DependencyResult dependency,
                                         Map<Object, DependencyNode> added,
                                         IdGenerator idGenerator) {

      Object selectedOrAttempted = dependency instanceof ResolvedDependencyResult
                                   ? getId(((ResolvedDependencyResult)dependency).getSelected())
                                   : ((UnresolvedDependencyResult)dependency).getAttempted();
      long id = idGenerator.getId(selectedOrAttempted);
      DependencyNode alreadySeenNode = added.get(id);
      if (alreadySeenNode != null) {
        return new ReferenceNode(id);
      }

      AbstractDependencyNode node;
      if (dependency instanceof ResolvedDependencyResult) {
        ResolvedComponentResult selected = ((ResolvedDependencyResult)dependency).getSelected();
        ComponentIdentifier componentId = getId(selected);
        if (componentId instanceof ProjectComponentIdentifier) {
          ProjectComponentIdentifier projectId = (ProjectComponentIdentifier)componentId;
          node = new ProjectDependencyNodeImpl(id, projectName(projectId), projectId.getProjectPath());
        }
        else if (componentId instanceof ModuleComponentIdentifier) {
          ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier)componentId;
          node = new ArtifactDependencyNodeImpl(id, moduleId.getGroup(), moduleId.getModule(), moduleId.getVersion());
        }
        else {
          node = new UnknownDependencyNode(id, componentId.getDisplayName());
        }

        node.setResolutionState(ResolutionState.RESOLVED);
        List<ComponentSelectionDescriptor> descriptions = selected.getSelectionReason().getDescriptions();
        if (!descriptions.isEmpty()) {
          node.setSelectionReason(descriptions.get(descriptions.size() - 1).getDescription());
        }

        added.put(id, node);
        for (DependencyResult child : selected.getDependencies()) {
          node.getDependencies().add(toNode(child, added, idGenerator));
        }
      }
      else {
        node = new UnknownDependencyNode(id, ((UnresolvedDependencyResult)dependency).getAttempted().getDisplayName());
        node.setResolutionState(ResolutionState.UNRESOLVED);
        added.put(id, node);
      }

      return node;
    }

    /**
     * The new version of Gradle we compile against has the getId method on a super-interface that didn't exist before Gradle 5.1
     * Use dynamic access to work around this.
     */
    private static ComponentIdentifier getId(ResolvedComponentResult result) {
      if (IS_51_OR_NEWER) {
        return result.getId();
      }
      else {
        return getIdDynamically(result);
      }
    }

    private static ComponentIdentifier getIdDynamically(ResolvedComponentResult result) {
      return result.getId();
    }

    private static final boolean IS_51_OR_NEWER = GradleVersion.current().compareTo(GradleVersion.version("5.1")) >= 0;
    private final AtomicLong nextId = new AtomicLong();

    private static class IdGenerator {
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

      private final HashMap<Object, Long> idMap = new HashMap<>();
      private final AtomicLong nextId;
    }
  }

  public static String projectName(ProjectComponentIdentifier identifier) {
    return is45OrNewer ? identifier.getProjectName() : identifier.getProjectPath();
  }
}

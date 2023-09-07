// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyGraphModel;

import com.intellij.openapi.externalSystem.model.project.dependencies.ComponentDependenciesImpl;
import com.intellij.openapi.externalSystem.model.project.dependencies.DependencyScopeNode;
import com.intellij.openapi.externalSystem.model.project.dependencies.ProjectDependencies;
import com.intellij.openapi.externalSystem.model.project.dependencies.ProjectDependenciesImpl;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil.JavaPluginUtil;

import static org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl.isIsNewDependencyResolutionApplicable;

public class GradleDependencyGraphModelBuilder implements ModelBuilderService {

  private final GradleDependencyReportGenerator reportGenerator = new GradleDependencyReportGenerator();

  @Override
  public boolean canBuild(String modelName) {
    return ProjectDependencies.class.getName().equals(modelName);
  }

  @Override
  public Object buildAll(String modelName, Project project) {
    boolean resolveSourceSetDependencies = Boolean.parseBoolean(System.getProperty("idea.resolveSourceSetDependencies", "false"));
    if (!resolveSourceSetDependencies || !isIsNewDependencyResolutionApplicable()) return null;

    SourceSetContainer sourceSetContainer = JavaPluginUtil.getSourceSetContainer(project);
    if (sourceSetContainer == null) return null;

    ProjectDependenciesImpl dependencies = new ProjectDependenciesImpl();
    for (SourceSet sourceSet : sourceSetContainer) {
      String compileConfigurationName = sourceSet.getCompileClasspathConfigurationName();
      Configuration compileConfiguration = project.getConfigurations().findByName(compileConfigurationName);
      if (compileConfiguration == null) continue;

      String runtimeConfigurationName = sourceSet.getRuntimeClasspathConfigurationName();
      Configuration runtimeConfiguration = project.getConfigurations().findByName(runtimeConfigurationName);
      if (runtimeConfiguration == null) continue;

      DependencyScopeNode compileScopeNode = reportGenerator.buildDependencyGraph(compileConfiguration, project);
      DependencyScopeNode runtimeScopeNode = reportGenerator.buildDependencyGraph(runtimeConfiguration, project);

      if (!compileScopeNode.getDependencies().isEmpty() || !runtimeScopeNode.getDependencies().isEmpty()) {
        dependencies.add(new ComponentDependenciesImpl(sourceSet.getName(), compileScopeNode, runtimeScopeNode));
      }
    }

    return dependencies.getComponentsDependencies().isEmpty() ? null : dependencies;
  }


  @NotNull
  @Override
  public ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
    return ErrorMessageBuilder.create(project, e, "Dependency graph model errors");
  }
}
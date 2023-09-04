// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.builder;

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
import org.jetbrains.plugins.gradle.tooling.tasks.DependenciesReport;
import com.intellij.gradle.toolingExtension.impl.util.javaPlugin.JavaPluginUtil;

import static org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl.isIsNewDependencyResolutionApplicable;

public class DependencyGraphModelBuilderImpl implements ModelBuilderService {

  private final DependenciesReport.ReportGenerator reportGenerator = new DependenciesReport.ReportGenerator();

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

      DependencyScopeNode compileScopeNode = reportGenerator.buildDependenciesGraph(compileConfiguration, project);
      DependencyScopeNode runtimeScopeNode = reportGenerator.buildDependenciesGraph(runtimeConfiguration, project);

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
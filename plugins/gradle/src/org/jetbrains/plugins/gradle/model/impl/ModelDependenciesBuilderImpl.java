/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.model.impl;

import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ModelBuilderService;
import org.jetbrains.plugins.gradle.model.ProjectDependenciesModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 11/5/13
 */
public class ModelDependenciesBuilderImpl implements ModelBuilderService {

  @Override
  public boolean canBuild(String modelName) {
    return ProjectDependenciesModel.class.getName().equals(modelName);
  }

  @Nullable
  @Override
  public Object buildAll(String modelName, Project project) {
    List<GradleDependency> dependencies = new ArrayList<GradleDependency>();

    for (Configuration configuration : project.getConfigurations()) {
      for (Dependency dependency : configuration.getDependencies()) {
        if (dependency instanceof ClientModule) {
          ClientModule clientModule = (ClientModule)dependency;
          for (ModuleDependency moduleDependency : clientModule.getDependencies()) {
            dependencies.add(
              new GradleDependencyImpl(
                configuration.getName(),
                moduleDependency.getName(),
                moduleDependency.getGroup(),
                moduleDependency.getVersion()
              ));
            ResolvedDependency resolvedDependency =
              findResolvedDependency(moduleDependency, configuration.getResolvedConfiguration().getFirstLevelModuleDependencies());
            if (resolvedDependency != null) {
              addTransitiveDependencies(dependencies, configuration.getName(), resolvedDependency.getChildren());
            }
          }
        }
        dependencies.add(
          new GradleDependencyImpl(
            configuration.getName(),
            dependency.getName(),
            dependency.getGroup(),
            dependency.getVersion()
          ));
      }
    }
    return new ProjectDependenciesModelImpl(project.getPath(), dependencies);
  }

  private static void addTransitiveDependencies(List<GradleDependency> dependencies,
                                                String configurationName,
                                                Set<ResolvedDependency> resolvedDependencies) {
    if (resolvedDependencies == null) return;
    for (ResolvedDependency resolvedDependency : resolvedDependencies) {
      dependencies.add(
        new GradleDependencyImpl(
          configurationName,
          resolvedDependency.getModuleName(),
          resolvedDependency.getModuleGroup(),
          resolvedDependency.getModuleVersion()
        ));
      addTransitiveDependencies(dependencies, configurationName, resolvedDependency.getChildren());
    }
  }

  private static ResolvedDependency findResolvedDependency(ModuleDependency moduleDependency,
                                                           Set<ResolvedDependency> resolvedDependencies) {
    for (ResolvedDependency resolvedDependency : resolvedDependencies) {
      ResolvedDependency dependency = findResolvedDependency(moduleDependency, resolvedDependency.getChildren());
      if (dependency != null) return dependency;
      if (isEqual(resolvedDependency.getModuleName(), moduleDependency.getName()) &&
          isEqual(resolvedDependency.getModuleGroup(), moduleDependency.getGroup()) &&
          isEqual(resolvedDependency.getModuleVersion(), moduleDependency.getVersion())) {
        return resolvedDependency;
      }
    }
    return null;
  }

  private static boolean isEqual(String str1, String str2) {
    return str1 != null ? str1.equals(str2) : str2 == null;
  }
}

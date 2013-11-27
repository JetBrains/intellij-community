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
package org.jetbrains.plugins.gradle.model.internal;

import org.gradle.tooling.model.idea.IdeaDependency;
import org.jetbrains.plugins.gradle.model.ProjectDependenciesModel;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 11/8/13
 */
public class ProjectDependenciesModelImpl implements ProjectDependenciesModel {
  private final String projectName;
  private final List<IdeaDependency> myDependencies;

  public ProjectDependenciesModelImpl(String projectName, List<IdeaDependency> dependencies) {
    this.projectName = projectName;
    myDependencies = dependencies;
  }

  @Override
  public String getProjectName() {
    return projectName;
  }

  @Override
  public List<IdeaDependency> getDependencies() {
    return myDependencies;
  }

  @Override
  public String toString() {
    return "ProjectDependenciesModelImpl{" +
           "projectName='" + projectName + '\'' +
           '}';
  }
}

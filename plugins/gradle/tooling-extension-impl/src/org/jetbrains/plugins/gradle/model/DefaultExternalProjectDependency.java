/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.model;

import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Vladislav.Soroka
 * @since 12/24/2014
 */
public class DefaultExternalProjectDependency extends AbstractExternalDependency implements ExternalProjectDependency {

  private static final long serialVersionUID = 1L;

  private String myProjectPath;
  private Collection<String> myProjectDependencyArtifacts;

  public DefaultExternalProjectDependency() {
  }

  public DefaultExternalProjectDependency(ExternalProjectDependency dependency) {
    super(dependency);
    myProjectPath = dependency.getProjectPath();
    myProjectDependencyArtifacts =
      dependency.getProjectDependencyArtifacts() == null
      ? new ArrayList<String>()
      : new ArrayList<String>(dependency.getProjectDependencyArtifacts());
  }

  @Override
  public String getProjectPath() {
    return myProjectPath;
  }

  public void setProjectPath(String projectPath) {
    this.myProjectPath = projectPath;
  }

  @Override
  public Collection<String> getProjectDependencyArtifacts() {
    return myProjectDependencyArtifacts;
  }

  public void setProjectDependencyArtifacts(Collection<String> projectArtifacts) {
    myProjectDependencyArtifacts = projectArtifacts;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DefaultExternalProjectDependency)) return false;
    if (!super.equals(o)) return false;
    DefaultExternalProjectDependency that = (DefaultExternalProjectDependency)o;
    return Objects.equal(myProjectPath, that.myProjectPath);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), myProjectPath);
  }

  @Override
  public String toString() {
    return "project dependency '" + myProjectPath + '\'';
  }
}

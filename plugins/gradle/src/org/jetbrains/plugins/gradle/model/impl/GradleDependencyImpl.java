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

/**
 * @author Vladislav.Soroka
 * @since 11/8/13
 */
public class GradleDependencyImpl implements GradleDependency {
  private final String configurationName;
  private final String dependencyName;
  private final String dependencyGroup;
  private final String dependencyVersion;

  public GradleDependencyImpl(String configurationName, String dependencyName, String dependencyGroup, String dependencyVersion) {
    this.configurationName = configurationName;
    this.dependencyName = dependencyName;
    this.dependencyGroup = dependencyGroup;
    this.dependencyVersion = dependencyVersion;
  }

  @Override
  public String getConfigurationName() {
    return configurationName;
  }

  @Override
  public String getDependencyName() {
    return dependencyName;
  }

  @Override
  public String getDependencyGroup() {
    return dependencyGroup;
  }

  @Override
  public String getDependencyVersion() {
    return dependencyVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleDependencyImpl that = (GradleDependencyImpl)o;

    if (dependencyGroup != null ? !dependencyGroup.equals(that.dependencyGroup) : that.dependencyGroup != null) return false;
    if (dependencyName != null ? !dependencyName.equals(that.dependencyName) : that.dependencyName != null) return false;
    if (dependencyVersion != null ? !dependencyVersion.equals(that.dependencyVersion) : that.dependencyVersion != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = dependencyName != null ? dependencyName.hashCode() : 0;
    result = 31 * result + (dependencyGroup != null ? dependencyGroup.hashCode() : 0);
    result = 31 * result + (dependencyVersion != null ? dependencyVersion.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "GradleDependencyImpl{" +
           "configurationName='" + configurationName + '\'' +
           ", dependencyName='" + dependencyName + '\'' +
           ", dependencyGroup='" + dependencyGroup + '\'' +
           ", dependencyVersion='" + dependencyVersion + '\'' +
           '}';
  }
}

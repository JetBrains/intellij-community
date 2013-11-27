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

import org.gradle.tooling.model.idea.IdeaDependencyScope;

import java.io.Serializable;

/**
 * @author Vladislav.Soroka
 * @since 11/8/13
 */
public abstract class AbstractGradleDependency implements Serializable {
  private final IdeaDependencyScope myScope;
  private final String dependencyName;
  private final String dependencyGroup;
  private final String dependencyVersion;

  public AbstractGradleDependency(IdeaDependencyScope myScope, String dependencyName, String dependencyGroup, String dependencyVersion) {
    this.myScope = myScope;
    this.dependencyName = dependencyName;
    this.dependencyGroup = dependencyGroup;
    this.dependencyVersion = dependencyVersion;
  }

  public IdeaDependencyScope getScope() {
    return myScope;
  }

  public boolean getExported() {
    return "compile".equals(myScope.getScope()) || "runtime".equals(myScope.getScope());
  }

  public String getDependencyName() {
    return dependencyName;
  }

  public String getDependencyGroup() {
    return dependencyGroup;
  }

  public String getDependencyVersion() {
    return dependencyVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AbstractGradleDependency)) return false;

    AbstractGradleDependency that = (AbstractGradleDependency)o;

    if (dependencyGroup != null ? !dependencyGroup.equals(that.dependencyGroup) : that.dependencyGroup != null) return false;
    if (dependencyName != null ? !dependencyName.equals(that.dependencyName) : that.dependencyName != null) return false;
    if (dependencyVersion != null ? !dependencyVersion.equals(that.dependencyVersion) : that.dependencyVersion != null) return false;
    if (myScope != null ? !myScope.equals(that.myScope) : that.myScope != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myScope != null ? myScope.hashCode() : 0;
    result = 31 * result + (dependencyName != null ? dependencyName.hashCode() : 0);
    result = 31 * result + (dependencyGroup != null ? dependencyGroup.hashCode() : 0);
    result = 31 * result + (dependencyVersion != null ? dependencyVersion.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "GradleDependency{" +
           "myScope=" + myScope +
           ", dependencyName='" + dependencyName + '\'' +
           ", dependencyGroup='" + dependencyGroup + '\'' +
           ", dependencyVersion='" + dependencyVersion + '\'' +
           ", exported=" + getExported() +
           '}';
  }
}

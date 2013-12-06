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
  private final String myDependencyName;
  private final String myDependencyGroup;
  private final String myDependencyVersion;
  private final String myClassifier;

  public AbstractGradleDependency(IdeaDependencyScope scope, String dependencyName,
                                  String dependencyGroup, String dependencyVersion, String classifier) {
    myScope = scope;
    myDependencyName = dependencyName;
    myDependencyGroup = dependencyGroup;
    myDependencyVersion = dependencyVersion;
    myClassifier = classifier;
  }

  public IdeaDependencyScope getScope() {
    return myScope;
  }

  public boolean getExported() {
    return "compile".equals(myScope.getScope()) || "runtime".equals(myScope.getScope());
  }

  public String getDependencyName() {
    return myDependencyName;
  }

  public String getDependencyGroup() {
    return myDependencyGroup;
  }

  public String getDependencyVersion() {
    return myDependencyVersion;
  }

  public String getClassifier() {
    return myClassifier;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AbstractGradleDependency)) return false;

    AbstractGradleDependency that = (AbstractGradleDependency)o;

    if (myDependencyGroup != null ? !myDependencyGroup.equals(that.myDependencyGroup) : that.myDependencyGroup != null) return false;
    if (myDependencyName != null ? !myDependencyName.equals(that.myDependencyName) : that.myDependencyName != null) return false;
    if (myDependencyVersion != null ? !myDependencyVersion.equals(that.myDependencyVersion) : that.myDependencyVersion != null) {
      return false;
    }
    if (myScope != null ? !myScope.equals(that.myScope) : that.myScope != null) return false;
    if (myClassifier != null ? !myClassifier.equals(that.myClassifier) : that.myClassifier != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myScope != null ? myScope.hashCode() : 0;
    result = 31 * result + (myDependencyName != null ? myDependencyName.hashCode() : 0);
    result = 31 * result + (myDependencyGroup != null ? myDependencyGroup.hashCode() : 0);
    result = 31 * result + (myDependencyVersion != null ? myDependencyVersion.hashCode() : 0);
    result = 31 * result + (myClassifier != null ? myClassifier.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "GradleDependency{" +
           "myScope=" + myScope +
           ", dependencyName='" + myDependencyName + '\'' +
           ", dependencyGroup='" + myDependencyGroup + '\'' +
           ", dependencyVersion='" + myDependencyVersion + '\'' +
           ", classifier='" + myClassifier + '\'' +
           ", exported=" + getExported() +
           '}';
  }
}

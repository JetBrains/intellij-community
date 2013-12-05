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

import org.gradle.plugins.ide.internal.IdeDependenciesExtractor;

/**
 * @author Vladislav.Soroka
 * @since 11/25/13
 */
public class DependencyVersionId {
  private final IdeDependenciesExtractor.IdeDependency myIdeDependency;
  private final String myName;
  private final String myGroup;
  private final String myVersion;
  private final String myClassifier;

  public DependencyVersionId(IdeDependenciesExtractor.IdeDependency dependency,
                             String name,
                             String group,
                             String version,
                             String classifier) {
    myIdeDependency = dependency;
    myName = name;
    myGroup = group;
    myVersion = version;
    myClassifier = classifier;
  }

  public String getName() {
    return myName;
  }

  public String getGroup() {
    return myGroup;
  }

  public String getVersion() {
    return myVersion;
  }

  public String getClassifier() {
    return myClassifier;
  }

  public IdeDependenciesExtractor.IdeDependency getIdeDependency() {
    return myIdeDependency;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DependencyVersionId)) return false;

    DependencyVersionId id = (DependencyVersionId)o;

    if (myGroup != null ? !myGroup.equals(id.myGroup) : id.myGroup != null) return false;
    if (myName != null ? !myName.equals(id.myName) : id.myName != null) return false;
    if (myVersion != null ? !myVersion.equals(id.myVersion) : id.myVersion != null) return false;
    if (myClassifier != null ? !myClassifier.equals(id.myClassifier) : id.myClassifier != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName != null ? myName.hashCode() : 0;
    result = 31 * result + (myGroup != null ? myGroup.hashCode() : 0);
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    result = 31 * result + (myClassifier != null ? myClassifier.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "DependencyVersionId{" +
           "name='" + myName + '\'' +
           ", group='" + myGroup + '\'' +
           ", version='" + myVersion + '\'' +
           ", classifier='" + myClassifier + '\'' +
           '}';
  }
}

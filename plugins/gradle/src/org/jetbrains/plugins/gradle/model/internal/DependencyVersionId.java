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
  private final String name;
  private final String group;
  private final String version;

  public DependencyVersionId(IdeDependenciesExtractor.IdeDependency dependency, String name, String group, String version) {
    myIdeDependency = dependency;
    this.name = name;
    this.group = group;
    this.version = version;
  }

  public String getName() {
    return name;
  }

  public String getGroup() {
    return group;
  }

  public String getVersion() {
    return version;
  }

  public IdeDependenciesExtractor.IdeDependency getIdeDependency() {
    return myIdeDependency;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DependencyVersionId)) return false;

    DependencyVersionId id = (DependencyVersionId)o;

    if (group != null ? !group.equals(id.group) : id.group != null) return false;
    if (name != null ? !name.equals(id.name) : id.name != null) return false;
    if (version != null ? !version.equals(id.version) : id.version != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (group != null ? group.hashCode() : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "DependencyVersionId{" +
           "name='" + name + '\'' +
           ", group='" + group + '\'' +
           ", version='" + version + '\'' +
           '}';
  }
}

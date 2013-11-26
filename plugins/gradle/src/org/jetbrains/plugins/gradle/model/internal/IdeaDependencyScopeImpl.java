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
import org.jetbrains.plugins.gradle.model.GradleDependencyScope;

import java.io.Serializable;

/**
 * @author Vladislav.Soroka
 * @since 11/25/13
 */
public class IdeaDependencyScopeImpl implements IdeaDependencyScope, Serializable {

  private final GradleDependencyScope myDependencyScope;

  public IdeaDependencyScopeImpl(GradleDependencyScope scope) {
    myDependencyScope = scope;
  }

  @Override
  public String getScope() {
    return myDependencyScope.getIdeaMappingName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IdeaDependencyScopeImpl)) return false;

    IdeaDependencyScopeImpl scope = (IdeaDependencyScopeImpl)o;

    if (myDependencyScope != scope.myDependencyScope) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myDependencyScope != null ? myDependencyScope.hashCode() : 0;
  }

  @Override
  public String toString() {
    return "IdeaDependencyScope{" + myDependencyScope + '}';
  }
}

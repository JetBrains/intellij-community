/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.tooling.internal;

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.HierarchicalElement;
import org.gradle.tooling.model.idea.*;

import java.io.Serializable;

/**
 * @author Vladislav.Soroka
 * @since 11/25/13
 */
public class StubIdeaModule implements IdeaModule, Serializable {

  private final String name;

  public StubIdeaModule(String name) {
    this.name = name;
  }

  @Override
  public DomainObjectSet<? extends IdeaContentRoot> getContentRoots() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GradleProject getGradleProject() {
    throw new UnsupportedOperationException();
  }

  @Override
  public IdeaProject getParent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public DomainObjectSet<? extends HierarchicalElement> getChildren() {
    throw new UnsupportedOperationException();
  }

  @Override
  public IdeaProject getProject() {
    throw new UnsupportedOperationException();
  }

  @Override
  public IdeaCompilerOutput getCompilerOutput() {
    throw new UnsupportedOperationException();
  }

  @Override
  public DomainObjectSet<? extends IdeaDependency> getDependencies() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDescription() {
    throw new UnsupportedOperationException();
  }
}

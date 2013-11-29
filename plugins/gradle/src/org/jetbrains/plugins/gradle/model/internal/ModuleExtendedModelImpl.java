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

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.jetbrains.plugins.gradle.model.ExtIdeaContentRoot;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 11/8/13
 */
public class ModuleExtendedModelImpl implements ModuleExtendedModel {
  private final String myName;
  private final String myGroup;
  private final String myVersion;
  private List<File> myArtifacts;
  private Set<ExtIdeaContentRoot> myContentRoots;

  public ModuleExtendedModelImpl(String name, String group, String version) {
    myName = name;
    myGroup = group;
    myVersion = version;
    myArtifacts = Collections.emptyList();
    myContentRoots = Collections.emptySet();
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String getGroup() {
    return myGroup;
  }

  @Override
  public String getVersion() {
    return myVersion;
  }

  @Override
  public List<File> getArtifacts() {
    return myArtifacts;
  }

  public void setArtifacts(List<File> artifacts) {
    this.myArtifacts = artifacts == null ? Collections.<File>emptyList() : artifacts;
  }

  @Override
  public DomainObjectSet<? extends ExtIdeaContentRoot> getContentRoots() {
    return ImmutableDomainObjectSet.of(myContentRoots);
  }

  public void setContentRoots(Set<ExtIdeaContentRoot> contentRoots) {
    myContentRoots = contentRoots == null ? Collections.<ExtIdeaContentRoot>emptySet() : contentRoots;
  }
}

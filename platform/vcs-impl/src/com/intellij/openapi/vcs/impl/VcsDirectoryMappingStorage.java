// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@State(name = "VcsDirectoryMappings", storages = @Storage("vcs.xml"))
final class VcsDirectoryMappingStorage implements PersistentStateComponent<Element> {
  @NotNull private final Project myProject;

  VcsDirectoryMappingStorage(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public Element getState() {
    Element e = new Element("state");
    ProjectLevelVcsManagerImpl.getInstanceImpl(myProject).writeDirectoryMappings(e);
    return e;
  }

  @Override
  public void loadState(@NotNull Element state) {
    ProjectLevelVcsManagerImpl.getInstanceImpl(myProject).readDirectoryMappings(state);
  }
}

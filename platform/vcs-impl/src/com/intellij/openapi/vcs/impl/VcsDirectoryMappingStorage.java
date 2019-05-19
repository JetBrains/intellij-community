// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

@State(name = "VcsDirectoryMappings", storages = @Storage("vcs.xml"))
public class VcsDirectoryMappingStorage implements PersistentStateComponent<Element> {
  private final ProjectLevelVcsManager myVcsManager;

  public VcsDirectoryMappingStorage(@NotNull ProjectLevelVcsManager vcsManager) {
    myVcsManager = vcsManager;
  }

  @Override
  public Element getState() {
    final Element e = new Element("state");
    ((ProjectLevelVcsManagerImpl) myVcsManager).writeDirectoryMappings(e);
    return e;
  }

  @Override
  public void loadState(@NotNull Element state) {
    ((ProjectLevelVcsManagerImpl) myVcsManager).readDirectoryMappings(state);
  }
}

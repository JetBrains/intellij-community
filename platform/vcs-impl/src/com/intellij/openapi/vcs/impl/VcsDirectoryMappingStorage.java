// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
@State(name = "VcsDirectoryMappings", storages = @Storage("vcs.xml"))
public class VcsDirectoryMappingStorage extends AbstractProjectComponent implements PersistentStateComponent<Element> {
  private final ProjectLevelVcsManager myVcsManager;

  public VcsDirectoryMappingStorage(final ProjectLevelVcsManager vcsManager, Project project) {
    super(project);
    myVcsManager = vcsManager;
  }

  public Element getState() {
    final Element e = new Element("state");
    ((ProjectLevelVcsManagerImpl) myVcsManager).writeDirectoryMappings(e);
    return e;
  }

  public void loadState(@NotNull Element state) {
    ((ProjectLevelVcsManagerImpl) myVcsManager).readDirectoryMappings(state);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "VcsDirectoryMappings";
  }
}

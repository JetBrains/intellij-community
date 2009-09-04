/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
@State(
  name = "VcsDirectoryMappings",
  storages = {
    @Storage(
      id ="other",
      file = "$PROJECT_FILE$"
    )
    ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/vcs.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
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

  public void loadState(Element state) {
    ((ProjectLevelVcsManagerImpl) myVcsManager).readDirectoryMappings(state);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "VcsDirectoryMappings";
  }
}

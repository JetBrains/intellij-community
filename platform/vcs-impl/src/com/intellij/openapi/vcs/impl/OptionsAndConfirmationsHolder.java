// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.projectlevelman.OptionsAndConfirmations;
import com.intellij.openapi.vcs.impl.projectlevelman.ProjectLevelVcsManagerSerialization;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
@State(name = "ProjectLevelVcsManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@SuppressWarnings("LightServiceMigrationCode")
public final class OptionsAndConfirmationsHolder implements PersistentStateComponent<Element> {
  private final OptionsAndConfirmations myOptionsAndConfirmations;

  public static @NotNull OptionsAndConfirmationsHolder getInstance(@NotNull Project project) {
    return project.getService(OptionsAndConfirmationsHolder.class);
  }

  public OptionsAndConfirmationsHolder() {
    myOptionsAndConfirmations = new OptionsAndConfirmations();
  }

  @Override
  public @NotNull Element getState() {
    Element element = new Element("state");
    ProjectLevelVcsManagerSerialization.writeExternalUtil(element, myOptionsAndConfirmations);
    return element;
  }

  @Override
  public void loadState(@NotNull Element state) {
    ProjectLevelVcsManagerSerialization.readExternalUtil(state, myOptionsAndConfirmations);
  }

  public @NotNull OptionsAndConfirmations getOptionsAndConfirmations() {
    return myOptionsAndConfirmations;
  }
}

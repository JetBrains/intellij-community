// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.projectlevelman.OptionsAndConfirmations;
import com.intellij.openapi.vcs.impl.projectlevelman.ProjectLevelVcsManagerSerialization;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
@State(name = "ProjectLevelVcsManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class OptionsAndConfirmationsHolder implements PersistentStateComponent<Element> {
  @NonNls private static final String SETTINGS_EDITED_MANUALLY = "settingsEditedManually";

  private final Project myProject;

  private final OptionsAndConfirmations myOptionsAndConfirmations;
  private boolean myHaveLegacyVcsConfiguration;

  public static @NotNull OptionsAndConfirmationsHolder getInstance(@NotNull Project project) {
    return project.getService(OptionsAndConfirmationsHolder.class);
  }

  public OptionsAndConfirmationsHolder(@NotNull Project project) {
    myProject = project;
    myOptionsAndConfirmations = new OptionsAndConfirmations();
  }

  @Override
  public @NotNull Element getState() {
    Element element = new Element("state");
    ProjectLevelVcsManagerSerialization.writeExternalUtil(element, myOptionsAndConfirmations);
    if (myHaveLegacyVcsConfiguration && !myProject.isDefault()) {
      element.setAttribute(SETTINGS_EDITED_MANUALLY, "true");
    }
    return element;
  }

  @Override
  public void loadState(@NotNull Element state) {
    ProjectLevelVcsManagerSerialization.readExternalUtil(state, myOptionsAndConfirmations);
    final Attribute attribute = state.getAttribute(SETTINGS_EDITED_MANUALLY);
    if (attribute != null) {
      try {
        myHaveLegacyVcsConfiguration = attribute.getBooleanValue();
      }
      catch (DataConversionException ignored) {
      }
    }
  }

  public boolean haveLegacyVcsConfiguration() {
    return myHaveLegacyVcsConfiguration;
  }

  public void markHasVcsConfiguration() {
    myHaveLegacyVcsConfiguration = true;
  }

  public @NotNull OptionsAndConfirmations getOptionsAndConfirmations() {
    return myOptionsAndConfirmations;
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class ShelfProjectConfigurable implements SearchableConfigurable {
  public static final String HELP_ID = "project.propVCSSupport.Shelf";

  @NotNull private final Project myProject;
  @Nullable private ShelfProjectConfigurationPanel myShelfConfigPanel;

  public ShelfProjectConfigurable(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public String getId() {
    return "Shelf.Project.Settings";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myShelfConfigPanel = new ShelfProjectConfigurationPanel(myProject);
    return myShelfConfigPanel;
  }

  @Override
  public boolean isModified() {
    return Objects.requireNonNull(myShelfConfigPanel).isModified();
  }

  @Override
  public void apply() {
    Objects.requireNonNull(myShelfConfigPanel).apply();
  }

  @Override
  public void reset() {
    Objects.requireNonNull(myShelfConfigPanel).restoreFromSettings();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return getDISPLAY_NAME();
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return HELP_ID;
  }

  public static String getDISPLAY_NAME() {
    return VcsBundle.message("shelf.tab");
  }
}

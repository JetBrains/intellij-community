/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ShelfProjectConfigurable implements SearchableConfigurable {

  public static final String DISPLAY_NAME = VcsBundle.message("shelf.tab");
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
    return ObjectUtils.assertNotNull(myShelfConfigPanel).isModified();
  }

  @Override
  public void apply() {
    ObjectUtils.assertNotNull(myShelfConfigPanel).apply();
  }

  @Override
  public void reset() {
    ObjectUtils.assertNotNull(myShelfConfigPanel).restoreFromSettings();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return HELP_ID;
  }
}

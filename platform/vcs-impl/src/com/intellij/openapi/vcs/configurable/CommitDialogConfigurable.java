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

import com.intellij.openapi.options.ConfigurableBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfiguration;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class CommitDialogConfigurable extends ConfigurableBase<CommitDialogSettingsPanel, VcsConfiguration> {

  public static final String ID = "project.propVCSSupport.CommitDialog";
  public static final String DISPLAY_NAME = "Commit Dialog";
  @NonNls private static final String HELP_ID = "reference.settings.VCS.CommitDialog";

  @NotNull private final Project myProject;

  public CommitDialogConfigurable(@NotNull Project project) {
    super(ID, DISPLAY_NAME, HELP_ID);
    myProject = project;
  }

  @NotNull
  @Override
  protected VcsConfiguration getSettings() {
    return VcsConfiguration.getInstance(myProject);
  }

  @Override
  protected CommitDialogSettingsPanel createUi() {
    return new CommitDialogSettingsPanel(myProject);
  }
}
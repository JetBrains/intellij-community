/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.config.ui.CvsConfigurationPanel;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

class Cvs2Configurable implements Configurable {
  private CvsConfigurationPanel  myComponent = null;
  private final Project myProject;

  Cvs2Configurable(Project project) {
    myProject = project;
  }

  @NonNls
  @Override
  public String getDisplayName() {
    return CvsBundle.message("configurable.Cvs2Configurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.projectsettings.vcs.cvs.options";
  }

  @Override
  public JComponent createComponent() {
    myComponent = new CvsConfigurationPanel(myProject);
    myComponent.updateFrom(getCvsConfiguration(), getAppLevelConfiguration());
    return myComponent.getPanel();
  }

  private CvsApplicationLevelConfiguration getAppLevelConfiguration() {
    return CvsApplicationLevelConfiguration.getInstance();
  }

  private CvsConfiguration getCvsConfiguration() {
    return CvsConfiguration.getInstance(myProject);
  }

  @Override
  public boolean isModified() {
    return !myComponent.equalsTo(getCvsConfiguration(), getAppLevelConfiguration());
  }

  @Override
  public void apply() {
    myComponent.saveTo(getCvsConfiguration(), getAppLevelConfiguration());
  }

  @Override
  public void reset() {
    myComponent.updateFrom(getCvsConfiguration(), getAppLevelConfiguration());
  }

  @Override
  public void disposeUIResources() {
    myComponent = null;
  }
}

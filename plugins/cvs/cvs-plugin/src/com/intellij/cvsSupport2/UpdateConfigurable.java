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
import com.intellij.cvsSupport2.cvsoperations.cvsUpdate.ui.UpdateOptionsPanel;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;

import javax.swing.*;
import java.util.Collection;

public class UpdateConfigurable extends BaseConfigurable {
  private UpdateOptionsPanel myPanel;
  private final Project myProject;
  private final Collection<FilePath> myFiles;

  public UpdateConfigurable(Project project, Collection<FilePath> files) {
    myProject = project;
    myFiles = files;
  }


  public String getDisplayName() {
    return CvsBundle.getCvsDisplayName();
  }

  public String getHelpTopic() {
    return "reference.versionControl.cvs.options";
  }

  public JComponent createComponent() {
    myPanel = new UpdateOptionsPanel(myProject, myFiles);
    return myPanel.getPanel();
  }

  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  public void reset() {
    myPanel.reset();
  }

  public void disposeUIResources() {
    myPanel = null;
  }
}

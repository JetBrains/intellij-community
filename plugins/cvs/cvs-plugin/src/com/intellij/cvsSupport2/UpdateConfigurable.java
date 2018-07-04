// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.cvsoperations.cvsUpdate.ui.UpdateOptionsPanel;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;

import javax.swing.*;
import java.util.Collection;

public class UpdateConfigurable implements Configurable {
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

  @Override
  public String getHelpTopic() {
    return "reference.versionControl.cvs.options";
  }

  public JComponent createComponent() {
    myPanel = new UpdateOptionsPanel(myProject, myFiles);
    return myPanel.getPanel();
  }

  @Override
  public boolean isModified() {
    return false;
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

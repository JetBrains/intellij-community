/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.appengine.actions;

import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.packaging.artifacts.Artifact;

import javax.swing.*;

/**
 * @author nik
 */
public class UploadApplicationDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JComboBox myArtifactComboBox;

  public UploadApplicationDialog(Project project) {
    super(project, true);
    setTitle("Upload Application");
    setModal(true);
    AppEngineUtil.setupAppEngineArtifactCombobox(project, myArtifactComboBox, true);
    setOKButtonText("Upload");
    init();
  }

  public Artifact getSelectedArtifact() {
    return (Artifact)myArtifactComboBox.getSelectedItem();
  }

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}

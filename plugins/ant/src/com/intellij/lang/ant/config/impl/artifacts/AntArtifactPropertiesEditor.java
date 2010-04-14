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
package com.intellij.lang.ant.config.impl.artifacts;

import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.impl.artifacts.AntArtifactProperties;
import com.intellij.lang.ant.config.impl.TargetChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author nik
 */
public class AntArtifactPropertiesEditor extends ArtifactPropertiesEditor {
  private final AntArtifactProperties myProperties;
  private final Project myProject;
  private JPanel myMainPanel;
  private JCheckBox myRunTargetCheckBox;
  private FixedSizeButton mySelectTargetButton;
  private AntBuildTarget myTarget;
  private final boolean myPostProcessing;

  public AntArtifactPropertiesEditor(AntArtifactProperties properties, Project project, boolean postProcessing) {
    myProperties = properties;
    myProject = project;
    myPostProcessing = postProcessing;
    mySelectTargetButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        selectTarget();
      }
    });
    myRunTargetCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        mySelectTargetButton.setEnabled(myRunTargetCheckBox.isSelected());
        if (myRunTargetCheckBox.isSelected() && myTarget == null) {
          selectTarget();
        }
      }
    });
  }

  private void selectTarget() {
    final TargetChooserDialog dialog = new TargetChooserDialog(myProject, myTarget);
    dialog.show();
    if (dialog.isOK()) {
      myTarget = dialog.getSelectedTarget();
      updateLabel();
    }
  }

  private void updateLabel() {
    if (myTarget != null) {
      myRunTargetCheckBox.setText("Run Ant target '" + myTarget.getName() + "'");
    }
    else {
      myRunTargetCheckBox.setText("Run Ant target <none>");
    }
  }

  public String getTabName() {
    return myPostProcessing ? POST_PROCESSING_TAB : PRE_PROCESSING_TAB;
  }

  public void apply() {
    myProperties.setEnabled(myRunTargetCheckBox.isSelected());
    if (myTarget != null) {
      final VirtualFile file = myTarget.getModel().getBuildFile().getVirtualFile();
      if (file != null) {
        myProperties.setFileUrl(file.getUrl());
        myProperties.setTargetName(myTarget.getName());
        return;
      }
    }
    myProperties.setFileUrl(null);
    myProperties.setTargetName(null);
  }

  public JComponent createComponent() {
    return myMainPanel;
  }

  public boolean isModified() {
    if (myProperties.isEnabled() != myRunTargetCheckBox.isSelected()) return true;
    if (myTarget == null) {
      return myProperties.getFileUrl() != null;
    }
    if (!Comparing.equal(myTarget.getName(), myProperties.getTargetName())) return true;
    final VirtualFile file = myTarget.getModel().getBuildFile().getVirtualFile();
    return file != null && !Comparing.equal(file.getUrl(), myProperties.getFileUrl());
  }

  public void reset() {
    myRunTargetCheckBox.setSelected(myProperties.isEnabled());
    myTarget = myProperties.findTarget(AntConfiguration.getInstance(myProject));
    updateLabel();
  }

  public void disposeUIResources() {
  }
}

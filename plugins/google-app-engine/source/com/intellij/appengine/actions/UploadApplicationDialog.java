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
    AppEngineUtil.setupAppEngineArtifactCombobox(project, myArtifactComboBox);
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

package com.intellij.appengine.server.instance;

import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.javaee.run.configuration.CommonModel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.run.BuildArtifactsBeforeRunTaskProvider;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author nik
 */
public class AppEngineRunConfigurationEditor extends SettingsEditor<CommonModel> implements PanelWithAnchor {
  private JPanel myMainPanel;
  private JComboBox myArtifactComboBox;
  private JTextField myPortField;
  private RawCommandLineEditor myServerParametersEditor;
  private JBLabel myWebArtifactToDeployLabel;
  private JBLabel myPortLabel;
  private JBLabel myServerParametersLabel;
  private final Project myProject;
  private Artifact myLastSelectedArtifact;
  private JComponent anchor;

  public AppEngineRunConfigurationEditor(Project project) {
    myProject = project;
    myArtifactComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onArtifactChanged();
      }
    });

    setAnchor(myWebArtifactToDeployLabel);
  }

  private void onArtifactChanged() {
    final Artifact selectedArtifact = getSelectedArtifact();
    if (!Comparing.equal(myLastSelectedArtifact, selectedArtifact)) {
      if (myLastSelectedArtifact != null) {
        BuildArtifactsBeforeRunTaskProvider.setBuildArtifactBeforeRunOption(myMainPanel, myLastSelectedArtifact, false);
      }
      if (selectedArtifact != null) {
        BuildArtifactsBeforeRunTaskProvider.setBuildArtifactBeforeRunOption(myMainPanel, selectedArtifact, true);
      }
      myLastSelectedArtifact = selectedArtifact;
    }
  }

  protected void resetEditorFrom(CommonModel s) {
    final AppEngineServerModel serverModel = (AppEngineServerModel)s.getServerModel();
    myPortField.setText(String.valueOf(serverModel.getLocalPort()));
    final Artifact artifact = serverModel.getArtifact();
    myArtifactComboBox.setSelectedItem(artifact);
    if (artifact == null && myArtifactComboBox.getItemCount() == 1) {
      myArtifactComboBox.setSelectedIndex(0);
    }
    myServerParametersEditor.setDialogCaption("Server Parameters");
    myServerParametersEditor.setText(serverModel.getServerParameters());
  }

  protected void applyEditorTo(CommonModel s) throws ConfigurationException {
    final AppEngineServerModel serverModel = (AppEngineServerModel)s.getServerModel();
    try {
      serverModel.setPort(Integer.parseInt(myPortField.getText()));
    }
    catch (NumberFormatException e) {
      throw new ConfigurationException("'" + myPortField.getText() + "' is not valid port number");
    }
    serverModel.setServerParameters(myServerParametersEditor.getText());
    serverModel.setArtifact(getSelectedArtifact());
  }

  private Artifact getSelectedArtifact() {
    return (Artifact)myArtifactComboBox.getSelectedItem();
  }

  @NotNull
  protected JComponent createEditor() {
    AppEngineUtil.setupAppEngineArtifactCombobox(myProject, myArtifactComboBox, false);
    return myMainPanel;
  }

  protected void disposeEditor() {
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myWebArtifactToDeployLabel.setAnchor(anchor);
    myPortLabel.setAnchor(anchor);
    myServerParametersLabel.setAnchor(anchor);
  }
}

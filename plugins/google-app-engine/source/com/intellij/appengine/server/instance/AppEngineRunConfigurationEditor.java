package com.intellij.appengine.server.instance;

import com.intellij.javaee.run.configuration.CommonModel;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.appengine.facet.AppEngineFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class AppEngineRunConfigurationEditor extends SettingsEditor<CommonModel> {
  private JPanel myMainPanel;
  private JComboBox myAppEngineFacetComboBox;
  private JTextField myPortField;
  private final Project myProject;

  public AppEngineRunConfigurationEditor(Project project) {
    myProject = project;
  }

  protected void resetEditorFrom(CommonModel s) {
    final AppEngineServerModel serverModel = (AppEngineServerModel)s.getServerModel();
    myPortField.setText(String.valueOf(serverModel.getLocalPort()));
    final WebFacet webFacet = serverModel.getWebFacet();
    myAppEngineFacetComboBox.setSelectedItem(webFacet);
    if (webFacet == null && myAppEngineFacetComboBox.getItemCount() == 1) {
      myAppEngineFacetComboBox.setSelectedIndex(0);
    }
  }

  protected void applyEditorTo(CommonModel s) throws ConfigurationException {
    final AppEngineServerModel serverModel = (AppEngineServerModel)s.getServerModel();
    try {
      serverModel.setPort(Integer.parseInt(myPortField.getText()));
    }
    catch (NumberFormatException e) {
      throw new ConfigurationException("'" + myPortField.getText() + "' is not valid port number");
    }
    final AppEngineFacet appEngineFacet = (AppEngineFacet)myAppEngineFacetComboBox.getSelectedItem();
    serverModel.setWebFacet(appEngineFacet != null ? appEngineFacet.getWebFacet() : null);
  }

  @NotNull
  protected JComponent createEditor() {
    AppEngineUtil.setupAppEngineFacetCombobox(myProject, myAppEngineFacetComboBox);
    return myMainPanel;
  }

  protected void disposeEditor() {
  }
}

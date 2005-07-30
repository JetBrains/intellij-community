/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.build;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.j2ee.make.ModuleBuildProperties;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.DocumentAdapter;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;
import java.util.HashSet;

/**
 * User: anna
 * Date: Nov 24, 2004
 */
public class PluginModuleBuildConfEditor implements ModuleConfigurationEditor {
  private JPanel myWholePanel = new JPanel(new GridBagLayout());
  private JLabel myPluginXMLLabel = new JLabel("Path to META-INF" + File.separator + "plugin.xml:");
  private TextFieldWithBrowseButton myPluginXML = new TextFieldWithBrowseButton();

  private TextFieldWithBrowseButton myManifest = new TextFieldWithBrowseButton();
  private JCheckBox myUseUserManifest = new JCheckBox("Use user manifest:");

  private boolean myModified = false;

  private PluginModuleBuildProperties myBuildProperties;

  private HashSet<String> mySetDependencyOnPluginModule = new HashSet<String>();
  private Module myModule;
  public PluginModuleBuildConfEditor(ModuleConfigurationState state) {
    myModule = state.getRootModel().getModule();
    myBuildProperties = (PluginModuleBuildProperties)ModuleBuildProperties.getInstance(myModule);
  }

  public JComponent createComponent() {
    myPluginXML.addActionListener(new BrowseFilesListener(myPluginXML.getTextField(), "Select META-INF Directory Location", "The META-INF"+ File.separator + "plugin.xml will be saved in selected directory", BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR));
    myPluginXML.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        myModified = !myPluginXML.getText().equals(myBuildProperties.getPluginXmlPath());
      }
    });
    myManifest.addActionListener(new BrowseFilesListener(myManifest.getTextField(), "Select manifest.mf", "Selected manifest.mf will be included in resulting distribution", BrowseFilesListener.SINGLE_FILE_DESCRIPTOR));
    myManifest.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        myModified = !myManifest.getText().equals(myBuildProperties.getManifestPath());
      }
    });
    myManifest.setEnabled(myBuildProperties.isUseUserManifest());
    myUseUserManifest.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        final boolean selected = myUseUserManifest.isSelected();
        myModified = (myBuildProperties.isUseUserManifest() != selected);
        myManifest.setEnabled(selected);
      }
    });
    final GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                                         GridBagConstraints.HORIZONTAL, new Insets(2, 2, 2, 2), 0, 0);
    myWholePanel.add(myPluginXMLLabel, gc);
    myWholePanel.add(myPluginXML, gc);
    JPanel manifestPanel = new JPanel(new GridBagLayout());
    manifestPanel.setBorder(BorderFactory.createTitledBorder("Manifest Settings"));
    gc.insets.left = 0;
    manifestPanel.add(myUseUserManifest, gc);
    gc.insets.left = 2;
    gc.weighty = 1.0;
    manifestPanel.add(myManifest, gc);
    myWholePanel.add(manifestPanel, gc);
    myWholePanel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
    return myWholePanel;
  }

  public boolean isModified() {
    return myModified;
  }

  public void apply() throws ConfigurationException {
    if (!mySetDependencyOnPluginModule.isEmpty()) {
      throw new ConfigurationException("Unable to set dependency on plugin module.");
    }
    final File plugin = myBuildProperties.getPluginXmlPath() != null ? new File(myBuildProperties.getPluginXmlPath()) : null;
    final String newPluginPath = myPluginXML.getText() + File.separator + "META-INF" + File.separator + "plugin.xml";
    if (plugin != null &&
        plugin.exists() &&
        !plugin.getPath().equals(newPluginPath) &&
        Messages.showYesNoDialog(myModule.getProject(),
                                 "Delete " + plugin.getPath() + " ?",
                                 "Clean up META-INF directory", null) == DialogWrapper.OK_EXIT_CODE) {

      CommandProcessor.getInstance().executeCommand(myModule.getProject(),
                                                    new Runnable() {
                                                      public void run() {
                                                        FileUtil.delete(plugin.getParentFile());
                                                      }
                                                    },
                                                    "Remove old plugin.xml directory",
                                                    null);
    }
    myBuildProperties.setPluginXMLUrl(newPluginPath);
    myBuildProperties.setManifestUrl(myManifest.getText());
    myBuildProperties.setUseUserManifest(myUseUserManifest.isSelected());
    myModified = false;
  }

  public void reset() {
    myPluginXML.setText(myBuildProperties.getPluginXmlPath().substring(0, myBuildProperties.getPluginXmlPath().length() - "/META-INF/plugin.xml".length()));
    myManifest.setText(myBuildProperties.getManifestPath());
    myUseUserManifest.setSelected(myBuildProperties.isUseUserManifest());
    myModified = false;
  }

  public void disposeUIResources() {}

  public void saveData() {}

  public String getDisplayName() {
    return "Plugin Deployment";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/nodes/plugin.png");
  }

  public String getHelpTopic() {
    return null; //todo
  }

  public void moduleStateChanged() {
  }

}

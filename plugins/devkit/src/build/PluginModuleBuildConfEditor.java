/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * User: anna
 * Date: Nov 24, 2004
 */
public class PluginModuleBuildConfEditor implements ModuleConfigurationEditor {
  private final JPanel myWholePanel = new JPanel(new GridBagLayout());
  @NonNls private final JLabel myPluginXMLLabel = new JLabel(DevKitBundle.message("deployment.view.meta-inf.label", " META-INF" + File.separator + "plugin.xml:"));
  private final TextFieldWithBrowseButton myPluginXML = new TextFieldWithBrowseButton();

  private final TextFieldWithBrowseButton myManifest = new TextFieldWithBrowseButton();
  private final JCheckBox myUseUserManifest = new JCheckBox(DevKitBundle.message("manifest.use.user.defined"));

  private final PluginBuildConfiguration myBuildProperties;

  private final Module myModule;
  @NonNls private static final String META_INF = "META-INF";
  @NonNls private static final String PLUGIN_XML = "plugin.xml";
  @NonNls private static final String MANIFEST_MF = "manifest.mf";

  public PluginModuleBuildConfEditor(ModuleConfigurationState state) {
    myModule = state.getRootModel().getModule();
    myBuildProperties = PluginBuildConfiguration.getInstance(myModule);
  }

  public JComponent createComponent() {
    myPluginXML.addActionListener(new BrowseFilesListener(myPluginXML.getTextField(), DevKitBundle.message("deployment.directory.location", META_INF), DevKitBundle.message("saved.message.common", META_INF + File.separator + PLUGIN_XML), BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR));
    myManifest.addActionListener(new BrowseFilesListener(myManifest.getTextField(), DevKitBundle.message("deployment.view.select", MANIFEST_MF), DevKitBundle.message("manifest.selection", MANIFEST_MF), BrowseFilesListener.SINGLE_FILE_DESCRIPTOR));
    myManifest.setEnabled(myBuildProperties.isUseUserManifest());
    myUseUserManifest.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final boolean selected = myUseUserManifest.isSelected();

        myManifest.setEnabled(selected);
      }
    });
    final GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                                         GridBagConstraints.HORIZONTAL, JBUI.insets(2), 0, 0);
    myWholePanel.add(myPluginXMLLabel, gc);
    myWholePanel.add(myPluginXML, gc);
    JPanel manifestPanel = new JPanel(new GridBagLayout());
    manifestPanel.setBorder(IdeBorderFactory.createTitledBorder(DevKitBundle.message("manifest.settings"), true));
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
    final String pluginXmlPath = new File(myBuildProperties.getPluginXmlPath()).getParentFile().getParent(); //parent for meta-inf
    boolean modified = !Comparing.strEqual(myPluginXML.getText(), pluginXmlPath);
    final boolean selected = myUseUserManifest.isSelected();
    modified |= myBuildProperties.isUseUserManifest() != selected;
    if (selected) {
      modified |= !Comparing.strEqual(myManifest.getText(), myBuildProperties.getManifestPath());
    }
    return modified;
  }

  public void apply() throws ConfigurationException {
    if (myUseUserManifest.isSelected() && myManifest.getText() != null && !new File(myManifest.getText()).exists()){
      throw new ConfigurationException(DevKitBundle.message("error.file.not.found.message", myManifest.getText()));
    }
    final File plugin = new File(myBuildProperties.getPluginXmlPath());
    final String newPluginPath = myPluginXML.getText() + File.separator + META_INF + File.separator + PLUGIN_XML;
    if (plugin.exists() && !plugin.getPath().equals(newPluginPath) && 
        Messages.showYesNoDialog(myModule.getProject(), DevKitBundle.message("deployment.view.delete", plugin.getPath()),
                                 DevKitBundle.message("deployment.cleanup", META_INF), null) == Messages.YES) {

      CommandProcessor.getInstance().executeCommand(myModule.getProject(),
                                                    new Runnable() {
                                                      public void run() {
                                                        FileUtil.delete(plugin.getParentFile());
                                                      }
                                                    },
                                                    DevKitBundle.message("deployment.cleanup", META_INF),
                                                    null);
    }
    myBuildProperties.setPluginXmlPathAndCreateDescriptorIfDoesntExist(newPluginPath);
    myBuildProperties.setManifestPath(myManifest.getText());
    myBuildProperties.setUseUserManifest(myUseUserManifest.isSelected());
  }

  public void reset() {
    myPluginXML.setText(myBuildProperties.getPluginXmlPath().substring(0, myBuildProperties.getPluginXmlPath().length() - META_INF.length() - PLUGIN_XML.length() - 2));
    myManifest.setText(myBuildProperties.getManifestPath());
    myUseUserManifest.setSelected(myBuildProperties.isUseUserManifest());
  }

  public void disposeUIResources() {}

  public void saveData() {}

  public String getDisplayName() {
    return DevKitBundle.message("deployment.title");
  }

  public String getHelpTopic() {
    return "plugin.configuring";
  }

  public void moduleStateChanged() {
  }

}

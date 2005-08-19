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

import org.jetbrains.idea.devkit.DevKitBundle;

/**
 * User: anna
 * Date: Nov 24, 2004
 */
public class PluginModuleBuildConfEditor implements ModuleConfigurationEditor {
  private JPanel myWholePanel = new JPanel(new GridBagLayout());
  @SuppressWarnings({"HardCodedStringLiteral"})
  private JLabel myPluginXMLLabel = new JLabel(DevKitBundle.message("deployment.view.meta-inf.label", " META-INF" + File.separator + "plugin.xml:"));
  private TextFieldWithBrowseButton myPluginXML = new TextFieldWithBrowseButton();

  private TextFieldWithBrowseButton myManifest = new TextFieldWithBrowseButton();
  private JCheckBox myUseUserManifest = new JCheckBox(DevKitBundle.message("manifest.use.user.defined"));

  private boolean myModified = false;

  private PluginModuleBuildProperties myBuildProperties;

  private Module myModule;
  public PluginModuleBuildConfEditor(ModuleConfigurationState state) {
    myModule = state.getRootModel().getModule();
    myBuildProperties = (PluginModuleBuildProperties)ModuleBuildProperties.getInstance(myModule);
  }

  public JComponent createComponent() {
    //noinspection HardCodedStringLiteral
    myPluginXML.addActionListener(new BrowseFilesListener(myPluginXML.getTextField(), DevKitBundle.message("deployment.directory.location", "META-INF"), DevKitBundle.message("saved.message.common", "META-INF"+ File.separator + "plugin.xml"), BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR));
    myPluginXML.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        myModified = !myPluginXML.getText().equals(myBuildProperties.getPluginXmlPath());
      }
    });
    //noinspection HardCodedStringLiteral
    myManifest.addActionListener(new BrowseFilesListener(myManifest.getTextField(), DevKitBundle.message("deployment.view.select", "manifest.mf"), DevKitBundle.message("manifest.selection", "manifest.mf"), BrowseFilesListener.SINGLE_FILE_DESCRIPTOR));
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
    manifestPanel.setBorder(BorderFactory.createTitledBorder(DevKitBundle.message("manifest.settings")));
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
    final File plugin = myBuildProperties.getPluginXmlPath() != null ? new File(myBuildProperties.getPluginXmlPath()) : null;
    //noinspection HardCodedStringLiteral
    final String newPluginPath = myPluginXML.getText() + File.separator + "META-INF" + File.separator + "plugin.xml";
    if (plugin != null &&
        plugin.exists() &&
        !plugin.getPath().equals(newPluginPath) &&
        Messages.showYesNoDialog(myModule.getProject(),
                                 DevKitBundle.message("deployment.view.delete", plugin.getPath()),
                                 DevKitBundle.message("deployment.cleanup"), null) == DialogWrapper.OK_EXIT_CODE) {

      //noinspection HardCodedStringLiteral
      CommandProcessor.getInstance().executeCommand(myModule.getProject(),
                                                    new Runnable() {
                                                      public void run() {
                                                        FileUtil.delete(plugin.getParentFile());
                                                      }
                                                    },
                                                    DevKitBundle.message("deployment.cleanup", "META-INF"),
                                                    null);
    }
    myBuildProperties.setPluginXMLUrl(newPluginPath);
    myBuildProperties.setManifestUrl(myManifest.getText());
    myBuildProperties.setUseUserManifest(myUseUserManifest.isSelected());
    myModified = false;
  }

  public void reset() {
    //noinspection HardCodedStringLiteral
    myPluginXML.setText(myBuildProperties.getPluginXmlPath().substring(0, myBuildProperties.getPluginXmlPath().length() - "/META-INF/plugin.xml".length()));
    myManifest.setText(myBuildProperties.getManifestPath());
    myUseUserManifest.setSelected(myBuildProperties.isUseUserManifest());
    myModified = false;
  }

  public void disposeUIResources() {}

  public void saveData() {}

  public String getDisplayName() {
    return DevKitBundle.message("deployment.title");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/nodes/plugin.png");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String getHelpTopic() {
    return "plugin.configuring";
  }

  public void moduleStateChanged() {
  }

}

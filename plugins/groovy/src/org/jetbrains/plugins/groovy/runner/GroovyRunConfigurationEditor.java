/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.runner;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

import javax.swing.*;
import java.awt.*;

public class GroovyRunConfigurationEditor extends SettingsEditor<GroovyScriptRunConfiguration> {
  private DefaultComboBoxModel myModulesModel;
  private JComboBox myModulesBox;
  private JPanel myMainPanel;
  private RawCommandLineEditor myVMParameters;
  private RawCommandLineEditor myParameters;
  private JPanel scriptPathPanel;
  private JPanel workDirPanel;
  private JCheckBox myDebugCB;
  private JTextField scriptPathField;
  private JTextField workDirField;

  public GroovyRunConfigurationEditor() {

    scriptPathField = new JTextField();
    final BrowseFilesListener scriptBrowseListener = new BrowseFilesListener(scriptPathField,
        "Script Path",
        "Specify path to script",
        new FileChooserDescriptor(true, false, false, false, false, false) {
          public boolean isFileSelectable(VirtualFile file) {
            return file.getFileType() == GroovyFileType.GROOVY_FILE_TYPE;
          }
        });
    final FieldPanel scriptFieldPanel = new FieldPanel(scriptPathField, "Script path:", null, scriptBrowseListener, null);
    scriptPathPanel.setLayout(new BorderLayout());
    scriptPathPanel.add(scriptFieldPanel, BorderLayout.CENTER);

    workDirField = new JTextField();
    final BrowseFilesListener workDirBrowseFilesListener = new BrowseFilesListener(workDirField,
        "Working directory",
        "Specify working directory",
        BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    final FieldPanel workDirFieldPanel = new FieldPanel(workDirField, "Working directory:", null, workDirBrowseFilesListener, null);
    workDirPanel.setLayout(new BorderLayout());
    workDirPanel.add(workDirFieldPanel, BorderLayout.CENTER);
  }

  public void resetEditorFrom(GroovyScriptRunConfiguration configuration) {
    myVMParameters.setDialogCaption("VM Parameters");
    myVMParameters.setText(configuration.vmParams);

    myParameters.setDialogCaption("Script Parameters");
    myParameters.setText(configuration.scriptParams);

    scriptPathField.setText(configuration.scriptPath);
    workDirField.setText(configuration.getWorkDir());

    myDebugCB.setEnabled(true);
    myDebugCB.setSelected(configuration.isDebugEnabled);

    myModulesModel.removeAllElements();
    for (Module module : configuration.getValidModules()) {
      myModulesModel.addElement(module);
    }
    myModulesModel.setSelectedItem(configuration.getModule());
  }

  public void applyEditorTo(GroovyScriptRunConfiguration configuration) throws ConfigurationException {
    configuration.setModule((Module) myModulesBox.getSelectedItem());
    configuration.vmParams = myVMParameters.getText();
    configuration.isDebugEnabled = myDebugCB.isSelected();
    configuration.scriptParams = myParameters.getText();
    configuration.scriptPath = scriptPathField.getText();
    configuration.setWorkDir(workDirField.getText());
  }

  @NotNull
  public JComponent createEditor() {
    myModulesModel = new DefaultComboBoxModel();
    myModulesBox.setModel(myModulesModel);
    myDebugCB.setEnabled(true);
    myDebugCB.setSelected(false);

    myModulesBox.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, final Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final Module module = (Module) value;
        if (module != null) {
          setIcon(module.getModuleType().getNodeIcon(false));
          setText(module.getName());
        }
        return this;
      }
    });

    return myMainPanel;
  }

  public void disposeEditor() {
  }
}

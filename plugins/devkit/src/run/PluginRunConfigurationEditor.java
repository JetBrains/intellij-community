/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package org.jetbrains.idea.devkit.run;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;
import java.awt.*;

public class PluginRunConfigurationEditor extends SettingsEditor<PluginRunConfiguration> {

  private DefaultComboBoxModel myModulesModel = new DefaultComboBoxModel();
  private JComboBox myModules = new JComboBox(myModulesModel);
  private JLabel myModuleLabel = new JLabel("Choose classpath and jdk from module:");
  private LabeledComponent<RawCommandLineEditor> myVMParameters = new LabeledComponent<RawCommandLineEditor>();


  private PluginRunConfiguration myPRC;

  public PluginRunConfigurationEditor(PluginRunConfiguration prc) {
    myPRC = prc;
  }

  public void resetEditorFrom(PluginRunConfiguration prc) {
    myModules.setSelectedItem(prc.getModule());
    getVMParameters().setText(prc.VM_PARAMETERS);
  }


  public void applyEditorTo(PluginRunConfiguration prc) throws ConfigurationException {
    if (myModules.getSelectedItem() == null){
      throw new ConfigurationException("No module selected.");
    }
    prc.setModule(((Module)myModules.getSelectedItem()));
    prc.VM_PARAMETERS = getVMParameters().getText();
  }

  public JComponent createEditor() {
    myModulesModel = new DefaultComboBoxModel(myPRC.getModules());
    myModules.setModel(myModulesModel);
    myModules.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, final Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value != null) {
          setText(ApplicationManager.getApplication().runReadAction(new Computable<String >() {
            public String compute() {
              return ((Module)value).getName();
            }
          }));
          setIcon(((Module)value).getModuleType().getNodeIcon(true));
        }
        return this;
      }
    });
    JPanel wholePanel = new JPanel(new GridBagLayout());
    wholePanel.add(myModuleLabel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST,
                                                                            GridBagConstraints.NONE, new Insets(5, 0, 5, 0), 0, 0));
    wholePanel.add(myModules, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST,
                                                                            GridBagConstraints.HORIZONTAL, new Insets(5, 0, 5, 0), 0, 0));
    myVMParameters.setText("&VM Parameters");
    myVMParameters.setComponent(new RawCommandLineEditor());
    myVMParameters.getComponent().setDialodCaption(myVMParameters.getRawText());
    wholePanel.add(myVMParameters, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST,
                                                                            GridBagConstraints.HORIZONTAL, new Insets(5, 0, 5, 0), 0, 0));
    return wholePanel;
  }

  public RawCommandLineEditor getVMParameters() {
    return myVMParameters.getComponent();
  }

  public void disposeEditor() {
  }
}
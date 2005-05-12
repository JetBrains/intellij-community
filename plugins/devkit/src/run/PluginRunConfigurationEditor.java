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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public class PluginRunConfigurationEditor extends SettingsEditor<PluginRunConfiguration> {

  private DefaultComboBoxModel myModulesModel = new DefaultComboBoxModel();
  private JComboBox myModules = new JComboBox(myModulesModel);
  private JLabel myModuleLabel = new JLabel("Choose classpath and jdk from module:");
  private LabeledComponent<RawCommandLineEditor> myVMParameters = new LabeledComponent<RawCommandLineEditor>();

  private JCheckBox myShowLogs = new JCheckBox("Show idea.log");

  private PluginRunConfiguration myPRC;
  private static final Logger LOG = Logger.getInstance("org.jetbrains.devkit.ru.PluginRunConfigurationEditor");

  public PluginRunConfigurationEditor(final PluginRunConfiguration prc) {
    myPRC = prc;
    myShowLogs.setSelected(isShow(prc));
    myShowLogs.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        setShow(prc, myShowLogs.isSelected());
      }
    });
    myModules.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myModules.getSelectedItem() != null){
          prc.removeAllLogFiles();
          final ProjectJdk jdk = ModuleRootManager.getInstance((Module)myModules.getSelectedItem()).getJdk();
          if (jdk != null && jdk.getSdkType() instanceof IdeaJdk){
            final String sandboxHome = ((Sandbox)jdk.getSdkAdditionalData()).getSandboxHome();
            if (sandboxHome == null){
              return;
            }
            try {
              final String file = new File(sandboxHome).getCanonicalPath() + File.separator + "system" + File.separator + "log" + File.separator +
                                  "idea.log";
              if (new File(file).exists()){
                prc.addLogFile(file, myShowLogs.isSelected());
              }
            }
            catch (IOException e1) {
              LOG.error(e1);
            }
          }
        }
      }
    });
  }

  private void setShow(PluginRunConfiguration prc, Boolean show){
    final Map<String, Boolean> logFiles = prc.getLogFiles();
    for (Iterator<String> iterator = logFiles.keySet().iterator(); iterator.hasNext();) {
      String s = iterator.next();
      logFiles.put(s, show);
    }
  }

  private boolean isShow(PluginRunConfiguration prc){
    return prc.getLogFiles().values().contains(Boolean.TRUE);
  }

  public void resetEditorFrom(PluginRunConfiguration prc) {
    myModules.setSelectedItem(prc.getModule());
    getVMParameters().setText(prc.VM_PARAMETERS);
  }


  public void applyEditorTo(PluginRunConfiguration prc) throws ConfigurationException {
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
    myVMParameters.setText("&VM Parameters");
    myVMParameters.setComponent(new RawCommandLineEditor());
    myVMParameters.getComponent().setDialodCaption(myVMParameters.getRawText());
    GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 0, 5, 0), 0, 0);
    wholePanel.add(myVMParameters, gc);
    wholePanel.add(myShowLogs, gc);
    wholePanel.add(myModuleLabel, gc);
    gc.weighty = 1;
    wholePanel.add(myModules, gc);
    return wholePanel;
  }

  public RawCommandLineEditor getVMParameters() {
    return myVMParameters.getComponent();
  }

  public void disposeEditor() {
  }
}
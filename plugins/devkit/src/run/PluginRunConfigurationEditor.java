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
package org.jetbrains.idea.devkit.run;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.LogFileOptions;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;
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

public class PluginRunConfigurationEditor extends SettingsEditor<PluginRunConfiguration> implements PanelWithAnchor {
  private final ModulesComboBox myModules = new ModulesComboBox();
  private final JBLabel myModuleLabel = new JBLabel(ExecutionBundle.message("application.configuration.use.classpath.and.jdk.of.module.label"));
  private final LabeledComponent<RawCommandLineEditor> myVMParameters = new LabeledComponent<RawCommandLineEditor>();
  private final LabeledComponent<RawCommandLineEditor> myProgramParameters = new LabeledComponent<RawCommandLineEditor>();
  private JComponent anchor;
  private final JrePathEditor myJrePathEditor;

  @NonNls private final JCheckBox myShowLogs = new JCheckBox(DevKitBundle.message("show.smth", "idea.log"));

  private final PluginRunConfiguration myPRC;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.devkit.run.PluginRunConfigurationEditor");

  public PluginRunConfigurationEditor(final PluginRunConfiguration prc) {
    myPRC = prc;
    myShowLogs.setSelected(isShow(prc));
    myShowLogs.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        setShow(prc, myShowLogs.isSelected());
      }
    });
    myModules.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        final Module selectedModule = myModules.getSelectedModule();
        if (selectedModule != null){
          prc.removeAllLogFiles();
          Sdk jdk = ModuleRootManager.getInstance(selectedModule).getSdk();
          jdk = IdeaJdk.findIdeaJdk(jdk);
          if (jdk != null) {
            final String sandboxHome = ((Sandbox)jdk.getSdkAdditionalData()).getSandboxHome();
            if (sandboxHome == null){
              return;
            }
            try {
              @NonNls final String file = new File(sandboxHome).getCanonicalPath() + File.separator + "system" + File.separator + "log" + File.separator +
                                  "idea.log";
              if (new File(file).exists()){
                prc.addLogFile(file, DevKitBundle.message("idea.log.tab.title"), myShowLogs.isSelected());
              }
            }
            catch (IOException e1) {
              LOG.error(e1);
            }
          }
        }
      }
    });

    setAnchor(myModuleLabel);
    myJrePathEditor = new JrePathEditor(DefaultJreSelector.fromModuleDependencies(myModules, true));
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    this.anchor = anchor;
    myModuleLabel.setAnchor(anchor);
    myVMParameters.setAnchor(anchor);
    myProgramParameters.setAnchor(anchor);
  }

  private static void setShow(PluginRunConfiguration prc, boolean show){
    for (LogFileOptions logFile: prc.getLogFiles()) {
      logFile.setEnable(show);
    }
  }

  private static boolean isShow(PluginRunConfiguration prc){
    for (LogFileOptions logFile : prc.getLogFiles()) {
      if (logFile.isEnabled()) return true;
    }
    return false;
  }

  @Override
  public void resetEditorFrom(PluginRunConfiguration prc) {
    myModules.setSelectedModule(prc.getModule());
    getVMParameters().setText(prc.VM_PARAMETERS);
    getProgramParameters().setText(prc.PROGRAM_PARAMETERS);
    myJrePathEditor.setPathOrName(prc.getAlternativeJrePath(), prc.isAlternativeJreEnabled());
  }


  @Override
  public void applyEditorTo(PluginRunConfiguration prc) throws ConfigurationException {
    prc.setModule(myModules.getSelectedModule());
    prc.VM_PARAMETERS = getVMParameters().getText();
    prc.PROGRAM_PARAMETERS = getProgramParameters().getText();
    prc.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());
    prc.setAlternativeJreEnabled(myJrePathEditor.isAlternativeJreSelected());
  }

  @Override
  @NotNull
  public JComponent createEditor() {
    myModules.fillModules(myPRC.getProject(), PluginModuleType.getInstance());
    JPanel wholePanel = new JPanel(new GridBagLayout());
    myVMParameters.setText(DevKitBundle.message("vm.parameters"));
    myVMParameters.setComponent(new RawCommandLineEditor());
    myVMParameters.getComponent().setDialogCaption(myVMParameters.getRawText());
    myVMParameters.setLabelLocation(BorderLayout.WEST);

    myProgramParameters.setText(DevKitBundle.message("program.parameters"));
    myProgramParameters.setComponent(new RawCommandLineEditor());
    myProgramParameters.getComponent().setDialogCaption(myProgramParameters.getRawText());
    myProgramParameters.setLabelLocation(BorderLayout.WEST);

    GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                   JBUI.insets(2, 0, 0, 0), UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP);
    wholePanel.add(myVMParameters, gc);
    wholePanel.add(myProgramParameters, gc);
    gc.gridwidth = 1;
    gc.gridy = 3;
    gc.weightx = 0;
    wholePanel.add(myModuleLabel, gc);
    gc.weighty = 1;
    gc.gridx = 1;
    gc.weightx = 1;
    wholePanel.add(myModules, gc);
    gc.gridx = 0;
    gc.gridy = 4;
    gc.gridwidth = 2;

    wholePanel.add(myJrePathEditor, gc);
    gc.gridy = 5;
    wholePanel.add(myShowLogs, gc);
    return wholePanel;
  }

  public RawCommandLineEditor getVMParameters() {
    return myVMParameters.getComponent();
  }

  public RawCommandLineEditor getProgramParameters() {
    return myProgramParameters.getComponent();
  }
}

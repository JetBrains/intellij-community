// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import javax.swing.*;
import java.awt.*;

public class PluginRunConfigurationEditor extends SettingsEditor<PluginRunConfiguration> implements PanelWithAnchor {
  private final ModulesComboBox myModules = new ModulesComboBox();
  private final JBLabel myModuleLabel = new JBLabel(ExecutionBundle.message("application.configuration.use.classpath.and.jdk.of.module.label"));
  private final LabeledComponent<RawCommandLineEditor> myVMParameters = new LabeledComponent<>();
  private final LabeledComponent<RawCommandLineEditor> myProgramParameters = new LabeledComponent<>();
  private JComponent anchor;
  private final JrePathEditor myJrePathEditor;

  private final PluginRunConfiguration myPRC;

  public PluginRunConfigurationEditor(final PluginRunConfiguration prc) {
    myPRC = prc;
    setAnchor(myModuleLabel);
    myJrePathEditor = new JrePathEditor(DefaultJreSelector.fromModuleDependencies(myModules, true));
    myJrePathEditor.setAnchor(myModuleLabel);
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

  @Override
  public void resetEditorFrom(@NotNull PluginRunConfiguration prc) {
    myModules.setSelectedModule(prc.getModule());
    getVMParameters().setText(prc.VM_PARAMETERS);
    getProgramParameters().setText(prc.PROGRAM_PARAMETERS);
    myJrePathEditor.setPathOrName(prc.getAlternativeJrePath(), prc.isAlternativeJreEnabled());
  }


  @Override
  public void applyEditorTo(@NotNull PluginRunConfiguration prc) {
    prc.setModule(myModules.getSelectedModule());
    prc.VM_PARAMETERS = getVMParameters().getText();
    prc.PROGRAM_PARAMETERS = getProgramParameters().getText();
    prc.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());
    prc.setAlternativeJreEnabled(myJrePathEditor.isAlternativeJreSelected());
  }

  @Override
  public @NotNull JComponent createEditor() {
    myModules.fillModules(myPRC.getProject(), PluginModuleType.getInstance());
    JPanel wholePanel = new JPanel(new GridBagLayout());
    myVMParameters.setText(DevKitBundle.message("vm.parameters"));
    myVMParameters.setComponent(new RawCommandLineEditor());
    myVMParameters.getComponent().setDialogCaption(myVMParameters.getRawText());
    myVMParameters.setLabelLocation(BorderLayout.WEST);
    myVMParameters.setAnchor(myModuleLabel);

    myProgramParameters.setText(DevKitBundle.message("program.parameters"));
    myProgramParameters.setComponent(new RawCommandLineEditor());
    myProgramParameters.getComponent().setDialogCaption(myProgramParameters.getRawText());
    myProgramParameters.setLabelLocation(BorderLayout.WEST);
    myProgramParameters.setAnchor(myModuleLabel);

    GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                   JBUI.insets(2, 0, 0, 0), UIUtil.DEFAULT_HGAP, 0);
    wholePanel.add(myVMParameters, gc);
    wholePanel.add(myProgramParameters, gc);
    gc.gridwidth = 1;
    gc.gridy = 3;
    gc.weightx = 0;
    wholePanel.add(myModuleLabel, gc);
    gc.gridx = 1;
    gc.weightx = 1;
    wholePanel.add(myModules, gc);
    gc.gridx = 0;
    gc.gridy = 4;
    gc.gridwidth = 2;

    wholePanel.add(myJrePathEditor, gc);
    gc.weighty = 1;
    gc.gridy = 5;
    wholePanel.add(JBBox.createVerticalBox(), gc);
    return wholePanel;
  }

  public RawCommandLineEditor getVMParameters() {
    return myVMParameters.getComponent();
  }

  public RawCommandLineEditor getProgramParameters() {
    return myProgramParameters.getComponent();
  }
}

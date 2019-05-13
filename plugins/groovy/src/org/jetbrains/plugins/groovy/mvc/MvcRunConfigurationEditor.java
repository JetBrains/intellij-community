// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.mvc;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;
import java.util.HashMap;

public class MvcRunConfigurationEditor<T extends MvcRunConfiguration> extends SettingsEditor<T> implements PanelWithAnchor {
  protected ModulesComboBox myModulesBox;
  private JPanel myMainPanel;
  private RawCommandLineEditor myVMParameters;
  private JTextField myCommandLine;
  private JBLabel myVMParamsLabel;
  private JPanel myExtensionPanel;
  protected JCheckBox myDepsClasspath;
  private EnvironmentVariablesComponent myEnvVariablesComponent;
  private MvcFramework myFramework;
  private JComponent anchor;

  public MvcRunConfigurationEditor() {
    myCommandLine.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        commandLineChanged(getCommandLine());
      }
    });

    setAnchor(myEnvVariablesComponent.getLabel());
  }

  @Override
  protected void resetEditorFrom(@NotNull T configuration) {
    myFramework = configuration.getFramework();
    myVMParameters.setDialogCaption("VM Options");
    myVMParameters.setText(configuration.vmParams);
    myVMParamsLabel.setLabelFor(myVMParameters);

    myCommandLine.setText(configuration.cmdLine);

    myModulesBox.setModules(configuration.getValidModules());
    myModulesBox.setSelectedModule(configuration.getModule());

    commandLineChanged(getCommandLine());

    myEnvVariablesComponent.setEnvs(new HashMap<>(configuration.envs));
    myEnvVariablesComponent.setPassParentEnvs(configuration.passParentEnv);

    if (myDepsClasspath.isEnabled()) {
      myDepsClasspath.setSelected(configuration.depsClasspath);
    }
  }

  protected boolean isAvailableDepsClasspath() {
    return true;
  }

  protected void commandLineChanged(@NotNull String newText) {
    final Module module = getSelectedModule();
    final String depsClasspath = MvcFramework.getInstance(module) == null ? "" : myFramework.getApplicationClassPath(module).getPathsString();
    final boolean hasClasspath = StringUtil.isNotEmpty(depsClasspath);
    setCBEnabled(hasClasspath && isAvailableDepsClasspath(), myDepsClasspath);

    String presentable = "Add --classpath";
    if (hasClasspath) {
      presentable += ": " + (depsClasspath.length() > 70 ? depsClasspath.substring(0, 70) + "..." : depsClasspath);
    }
    myDepsClasspath.setText(presentable);
    myDepsClasspath.setToolTipText("<html>&nbsp;" + StringUtil.replace(depsClasspath, File.pathSeparator, "<br>&nbsp;") + "</html>");
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myVMParamsLabel.setAnchor(anchor);
    myEnvVariablesComponent.setAnchor(anchor);
  }

  protected static void setCBEnabled(boolean enabled, final JCheckBox checkBox) {
    final boolean wasEnabled = checkBox.isEnabled();
    checkBox.setEnabled(enabled);
    if (wasEnabled && !enabled) {
      checkBox.setSelected(false);
    } else if (!wasEnabled && enabled) {
      checkBox.setSelected(true);
    }
  }

  @Override
  protected void applyEditorTo(@NotNull T configuration) throws ConfigurationException {
    configuration.setModule(getSelectedModule());
    configuration.vmParams = myVMParameters.getText().trim();
    configuration.cmdLine = getCommandLine();
    configuration.envs.clear();
    configuration.envs.putAll(myEnvVariablesComponent.getEnvs());
    configuration.passParentEnv = myEnvVariablesComponent.isPassParentEnvs();

    if (myDepsClasspath.isEnabled()) {
      configuration.depsClasspath = myDepsClasspath.isSelected();
    }
  }

  protected String getCommandLine() {
    return myCommandLine.getText().trim();
  }

  protected Module getSelectedModule() {
    return myModulesBox.getSelectedModule();
  }

  public void addExtension(JComponent component) {
    myExtensionPanel.add(component, BorderLayout.PAGE_START);
  }

  @Override
  @NotNull
  protected JComponent createEditor() {
    return myMainPanel;
  }
}

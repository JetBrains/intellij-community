// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;

public final class GradleExternalTaskConfigurationType extends AbstractExternalSystemTaskConfigurationType {
  public GradleExternalTaskConfigurationType() {
    super(GradleConstants.SYSTEM_ID);
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.GradleRunConfiguration";
  }

  public static GradleExternalTaskConfigurationType getInstance() {
    return (GradleExternalTaskConfigurationType)ExternalSystemUtil.findConfigurationType(GradleConstants.SYSTEM_ID);
  }

  @NotNull
  @Override
  protected ExternalSystemRunConfiguration doCreateConfiguration(@NotNull ProjectSystemId externalSystemId,
                                                                 @NotNull Project project,
                                                                 @NotNull ConfigurationFactory factory,
                                                                 @NotNull String name) {
    return new GradleRunConfiguration(project, factory, name);
  }
}


class GradleRunConfiguration extends ExternalSystemRunConfiguration {

  public static final String DEBUG_FLAG_NAME = "GradleScriptDebugEnabled";
  private boolean isScriptDebugEnabled = false;

  public GradleRunConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(GradleConstants.SYSTEM_ID, project, factory, name);
  }

  public boolean isScriptDebugEnabled() {
    return isScriptDebugEnabled;
  }

  public void setScriptDebugEnabled(boolean scriptDebugEnabled) {
    isScriptDebugEnabled = scriptDebugEnabled;
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    final Element child = element.getChild(DEBUG_FLAG_NAME);
    if (child != null) {
      isScriptDebugEnabled = Boolean.valueOf(child.getText());
    }
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    final Element child = new Element(DEBUG_FLAG_NAME);
    child.setText(String.valueOf(isScriptDebugEnabled));
    element.addContent(child);
  }

  @NotNull
  @Override
  public SettingsEditor<ExternalSystemRunConfiguration> getConfigurationEditor() {
    final SettingsEditor<ExternalSystemRunConfiguration> editor = super.getConfigurationEditor();
    if (editor instanceof SettingsEditorGroup) {
      final SettingsEditorGroup group = (SettingsEditorGroup)editor;
      group.addEditor(GradleConstants.SYSTEM_ID.getReadableName(), new GradleDebugSettingsEditor());
    }
    return editor;
  }
}


class GradleDebugSettingsEditor extends SettingsEditor<GradleRunConfiguration> {

  JCheckBox myCheckBox;
  JLabel myLabel;

  @Override
  protected void resetEditorFrom(@NotNull GradleRunConfiguration s) {
    myCheckBox.setSelected(s.isScriptDebugEnabled());
  }

  @Override
  protected void applyEditorTo(@NotNull GradleRunConfiguration s) throws ConfigurationException {
    s.setScriptDebugEnabled(myCheckBox.isSelected());
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    JPanel panel = new JPanel();
    myLabel = new JLabel("Enable Gradle script debugging");
    myCheckBox = new JCheckBox("Debug gradle script");
    panel.add(myCheckBox);
    panel.add(myLabel);
    return panel;
  }
}

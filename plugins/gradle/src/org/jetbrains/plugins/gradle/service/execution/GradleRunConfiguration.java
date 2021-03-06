// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.LocatableRunConfigurationOptions;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.LanguageRuntimeType;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.GradleIdeManager;
import org.jetbrains.plugins.gradle.execution.target.GradleRuntimeType;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class GradleRunConfiguration extends ExternalSystemRunConfiguration implements SMRunnerConsolePropertiesProvider,
                                                                                      TargetEnvironmentAwareRunProfile {

  public static final String DEBUG_FLAG_NAME = "GradleScriptDebugEnabled";
  public static final String DEBUG_ALL_NAME = "DebugAllEnabled";
  public static final Key<Boolean> DEBUG_FLAG_KEY = Key.create("DEBUG_GRADLE_SCRIPT");
  public static final Key<Boolean> DEBUG_ALL_KEY = Key.create("DEBUG_ALL_TASKS");

  @ApiStatus.Internal
  public static final Key<String> DEBUGGER_PARAMETERS_KEY = Key.create("DEBUGGER_PARAMETERS");

  private boolean isDebugAllEnabled = false;

  public GradleRunConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(GradleConstants.SYSTEM_ID, project, factory, name);
    setDebugServerProcess(true);
    setReattachDebugProcess(true);
  }

  public boolean isScriptDebugEnabled() {
    return isDebugServerProcess();
  }

  public void setScriptDebugEnabled(boolean scriptDebugEnabled) {
    setDebugServerProcess(scriptDebugEnabled);
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) {
    putUserData(DEBUG_FLAG_KEY, Boolean.valueOf(isDebugServerProcess()));
    putUserData(DEBUG_ALL_KEY, Boolean.valueOf(isDebugAllEnabled));
    return super.getState(executor, env);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull LocatableRunConfigurationOptions getOptions() {
    return super.getOptions();
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    final Element child = element.getChild(DEBUG_FLAG_NAME);
    if (child != null) {
      setDebugServerProcess(Boolean.valueOf(child.getText()));
    }
    final Element debugAll = element.getChild(DEBUG_ALL_NAME);
    if (debugAll != null) {
      isDebugAllEnabled = Boolean.valueOf(debugAll.getText());
    }
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    final Element debugAll = new Element(DEBUG_ALL_NAME);
    debugAll.setText(String.valueOf(isDebugAllEnabled));
    element.addContent(debugAll);
  }

  @NotNull
  @Override
  public SettingsEditor<ExternalSystemRunConfiguration> getConfigurationEditor() {
    final SettingsEditor<ExternalSystemRunConfiguration> editor = super.getConfigurationEditor();
    if (editor instanceof SettingsEditorGroup) {
      final SettingsEditorGroup group = (SettingsEditorGroup)editor;
      //noinspection unchecked
      group.addEditor(GradleBundle.message("gradle.settings.title.debug"), new GradleDebugSettingsEditor());
    }
    return editor;
  }

  @NotNull
  @Override
  public SMTRunnerConsoleProperties createTestConsoleProperties(@NotNull Executor executor) {
    return GradleIdeManager.getInstance().createTestConsoleProperties(getProject(), executor, this);
  }

  public boolean isDebugAllEnabled() {
    return isDebugAllEnabled;
  }

  public void setDebugAllEnabled(boolean debugAllEnabled) {
    isDebugAllEnabled = debugAllEnabled;
  }

  @Override
  public boolean canRunOn(@NotNull TargetEnvironmentConfiguration target) {
    return true;
  }

  @Override
  public @Nullable LanguageRuntimeType<?> getDefaultLanguageRuntimeType() {
    return LanguageRuntimeType.EXTENSION_NAME.findExtension(GradleRuntimeType.class);
  }

  @Override
  public @Nullable String getDefaultTargetName() {
    return getOptions().getRemoteTarget();
  }

  @Override
  public void setDefaultTargetName(@Nullable String targetName) {
    getOptions().setRemoteTarget(targetName);
  }
}

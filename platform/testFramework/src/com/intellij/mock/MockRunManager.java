package com.intellij.mock;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author gregsh
 */
public class MockRunManager extends RunManagerEx {
  @NotNull
  @Override
  public ConfigurationType[] getConfigurationFactories() {
    return new ConfigurationType[0];
  }

  @NotNull
  @Override
  public RunConfiguration[] getConfigurations(@NotNull ConfigurationType type) {
    return new RunConfiguration[0];
  }

  @NotNull
  @Override
  public List<RunConfiguration> getConfigurationsList(@NotNull ConfigurationType type) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public RunConfiguration[] getAllConfigurations() {
    return new RunConfiguration[0];
  }

  @NotNull
  @Override
  public List<RunConfiguration> getAllConfigurationsList() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public RunConfiguration[] getTempConfigurations() {
    return new RunConfiguration[0];
  }

  @NotNull
  @Override
  public List<RunnerAndConfigurationSettings> getTempConfigurationsList() {
    return Collections.emptyList();
  }

  @Override
  public boolean isTemporary(@NotNull RunConfiguration configuration) {
    return false;
  }

  @Override
  public void makeStable(@NotNull RunConfiguration configuration) {
  }

  @Override
  public void makeStable(@NotNull RunnerAndConfigurationSettings settings) {
  }

  @Override
  public RunnerAndConfigurationSettings getSelectedConfiguration() {
    return null;
  }

  @NotNull
  @Override
  public RunnerAndConfigurationSettings createRunConfiguration(@NotNull String name, @NotNull ConfigurationFactory type) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public RunnerAndConfigurationSettings createConfiguration(@NotNull RunConfiguration runConfiguration, @NotNull ConfigurationFactory factory) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public RunnerAndConfigurationSettings getConfigurationTemplate(ConfigurationFactory factory) {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public RunnerAndConfigurationSettings[] getConfigurationSettings(@NotNull ConfigurationType type) {
    return new RunnerAndConfigurationSettings[0];
  }

  @Override
  @NotNull
  public List<RunnerAndConfigurationSettings> getConfigurationSettingsList(@NotNull ConfigurationType type) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Map<String, List<RunnerAndConfigurationSettings>> getStructure(@NotNull ConfigurationType type) {
    return Collections.emptyMap();
  }

  @NotNull
  @Override
  public List<RunnerAndConfigurationSettings> getAllSettings() {
    return Collections.emptyList();
  }

  @Override
  public void setSelectedConfiguration(RunnerAndConfigurationSettings configuration) {
  }

  @Override
  public void setTemporaryConfiguration(RunnerAndConfigurationSettings tempConfiguration) {
  }

  @Override
  public RunManagerConfig getConfig() {
    return null;
  }

  @NotNull
  @Override
  public RunnerAndConfigurationSettings createConfiguration(String name, ConfigurationFactory type) {
    return null;
  }

  @Override
  public void addConfiguration(RunnerAndConfigurationSettings settings,
                               boolean isShared,
                               List<BeforeRunTask> tasks,
                               boolean addTemplateTasksIfAbsent) {
  }

  @Override
  public void addConfiguration(RunnerAndConfigurationSettings settings, boolean isShared) {
  }

  @Override
  public boolean isConfigurationShared(RunnerAndConfigurationSettings settings) {
    return false;
  }

  @NotNull
  @Override
  public List<BeforeRunTask> getBeforeRunTasks(RunConfiguration settings) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public <T extends BeforeRunTask> List<T> getBeforeRunTasks(Key<T> taskProviderID) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public <T extends BeforeRunTask> List<T> getBeforeRunTasks(RunConfiguration settings, Key<T> taskProviderID) {
    return Collections.emptyList();
  }

  @Override
  public void setBeforeRunTasks(RunConfiguration runConfiguration, List<BeforeRunTask> tasks, boolean addEnabledTemplateTasksIfAbsent) {
  }

  @Override
  public RunnerAndConfigurationSettings findConfigurationByName(@NotNull String name) {
    return null;
  }

  @Override
  public Icon getConfigurationIcon(@NotNull RunnerAndConfigurationSettings settings) {
    return null;
  }

  @Override
  @NotNull
  public Collection<RunnerAndConfigurationSettings> getSortedConfigurations() {
    return Collections.emptyList();
  }

  @Override
  public void removeConfiguration(RunnerAndConfigurationSettings settings) {
  }

  @Override
  public void addRunManagerListener(RunManagerListener listener) {
  }

  @Override
  public void removeRunManagerListener(RunManagerListener listener) {
  }

  @Override
  public void refreshUsagesList(RunProfile profile) {
  }
}

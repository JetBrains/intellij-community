package com.intellij.mock;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
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
  public RunConfiguration[] getAllConfigurations() {
    return new RunConfiguration[0];
  }

  @NotNull
  @Override
  public RunConfiguration[] getTempConfigurations() {
    return new RunConfiguration[0];
  }

  @Override
  public boolean isTemporary(@NotNull RunConfiguration configuration) {
    return false;
  }

  @Override
  public void makeStable(RunConfiguration configuration) {
  }

  @Override
  public RunnerAndConfigurationSettings getSelectedConfiguration() {
    return null;
  }

  @NotNull
  @Override
  public RunnerAndConfigurationSettings createRunConfiguration(String name, ConfigurationFactory type) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public RunnerAndConfigurationSettings createConfiguration(RunConfiguration runConfiguration, ConfigurationFactory factory) {
    throw new UnsupportedOperationException();
  }

  @Override
  public RunnerAndConfigurationSettings[] getConfigurationSettings(@NotNull ConfigurationType type) {
    return new RunnerAndConfigurationSettings[0];
  }

  @Override
  public boolean isTemporary(@NotNull RunnerAndConfigurationSettings configuration) {
    return false;
  }

  @Override
  public void setSelectedConfiguration(RunnerAndConfigurationSettings configuration) {
  }

  @Override
  public void setTemporaryConfiguration(RunnerAndConfigurationSettings tempConfiguration) {
  }

  @Override
  public void setEditBeforeRun(RunConfiguration settings, boolean edit) {
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
                               Map<Key<? extends BeforeRunTask>, BeforeRunTask> tasks) {
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
  public <T extends BeforeRunTask> Map<Key<T>, BeforeRunTask> getBeforeRunTasks(RunConfiguration settings) {
    return Collections.emptyMap();
  }

  @Override
  public <T extends BeforeRunTask> T getBeforeRunTask(RunConfiguration settings, Key<T> taskProviderID) {
    return null;
  }

  @NotNull
  @Override
  public <T extends BeforeRunTask> Collection<T> getBeforeRunTasks(Key<T> taskProviderID, boolean includeOnlyActiveTasks) {
    return null;
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
  public void invalidateConfigurationIcon(@NotNull RunnerAndConfigurationSettings settings) {
  }

  @Override
  public Collection<RunnerAndConfigurationSettings> getSortedConfigurations() {
    return null;
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
}

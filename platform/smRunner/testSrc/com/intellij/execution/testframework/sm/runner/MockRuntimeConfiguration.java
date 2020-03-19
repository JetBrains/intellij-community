// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Roman Chernyatchik
 */
public final class MockRuntimeConfiguration extends LocatableConfigurationBase implements Cloneable, ModuleRunConfiguration {
  public MockRuntimeConfiguration(final Project project) {
    super(project, new MockConfigurationFactory(), "");
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    //Do nothing
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    //Do nothing
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    throw new UnsupportedOperationException();
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) {
    return null;
  }

  private static class MockConfigurationFactory extends ConfigurationFactory {
    MockConfigurationFactory() {
      super(new MyConfigurationType());
    }

    @NotNull
    @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new MockRuntimeConfiguration(project);
    }

  }

  private static class MyConfigurationType implements ConfigurationType {
    @NotNull
    @Override
    public String getDisplayName() {
      return "mock";
    }

    @Override
    public String getConfigurationTypeDescription() {
      return "mock type";
    }

    @Override
    public Icon getIcon() {
      return null;
    }

    @Override
    @NotNull
    public String getId() {
      return "MockRuntimeConfiguration";
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
      return ConfigurationFactory.EMPTY_ARRAY;
    }
  }
}

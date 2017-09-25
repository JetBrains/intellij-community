/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
public class MockRuntimeConfiguration extends LocatableConfigurationBase implements Cloneable, ModuleRunConfiguration {
  public MockRuntimeConfiguration(final Project project) {
    super(project, new MockConfigurationFactory(), "");
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    //Do nothing
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    //Do nothing
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return null;
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) {
    return null;
  }

  private static class MockConfigurationFactory extends ConfigurationFactory {
    public MockConfigurationFactory() {
      super(new MyConfigurationType());
    }

    @NotNull
    @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new MockRuntimeConfiguration(project);
    }

  }

  private static class MyConfigurationType implements ConfigurationType {
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
      return new ConfigurationFactory[0];
    }
  }
}

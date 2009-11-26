/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.junit;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JUnitConfigurationType implements LocatableConfigurationType {
  private static final Icon ICON = IconLoader.getIcon("/runConfigurations/junit.png");
  private final ConfigurationFactory myFactory;

  /**reflection*/
  public JUnitConfigurationType() {
    myFactory = new ConfigurationFactory(this) {
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new JUnitConfiguration("", project, this);
      }

      @Override
      public Icon getIcon(@NotNull final RunConfiguration configuration) {
        return RunConfigurationExtension.getIcon((JUnitConfiguration)configuration, getIcon());
      }
    };
  }

  public String getDisplayName() {
    return ExecutionBundle.message("junit.configuration.display.name");
  }

  public String getConfigurationTypeDescription() {
    return ExecutionBundle.message("junit.configuration.description");
  }

  public Icon getIcon() {
    return ICON;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  public RunnerAndConfigurationSettings createConfigurationByLocation(final Location location) {
    return null;
  }

  public boolean isConfigurationByLocation(final RunConfiguration configuration, final Location location) {
    final JUnitConfiguration unitConfiguration = (JUnitConfiguration)configuration;
    final TestObject testobject = unitConfiguration.getTestObject();
    if (testobject == null) {
      return false;
    }
    else {
      final PsiElement element = location.getPsiElement();
      if (testobject.isConfiguredByElement(unitConfiguration, element)) {
        final Module configurationModule = unitConfiguration.getConfigurationModule().getModule();
        if (Comparing.equal(location.getModule(), configurationModule)) return true;

        final Module predefinedModule =
          ((JUnitConfiguration)((RunManagerImpl)RunManagerEx.getInstanceEx(location.getProject())).getConfigurationTemplate(myFactory)
            .getConfiguration()).getConfigurationModule().getModule();
        return Comparing.equal(predefinedModule, configurationModule);
      }
      else {
        return false;
      }
    }
  }

  @NotNull
  public String getId() {
    return "JUnit";
  }

  @Nullable
  public static JUnitConfigurationType getInstance() {
    return ContainerUtil.findInstance(Extensions.getExtensions(CONFIGURATION_TYPE_EP), JUnitConfigurationType.class);
  }
}

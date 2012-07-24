/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class AndroidRunConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory = new ConfigurationFactory(this) {
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new AndroidRunConfiguration("", project, this);
    }

    @Override
    public boolean canConfigurationBeSingleton() {
      return false;
    }
  };

  public static AndroidRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(AndroidRunConfigurationType.class);
  }

  public String getDisplayName() {
    return AndroidBundle.message("android.run.configuration.type.name");
  }

  public String getConfigurationTypeDescription() {
    return AndroidBundle.message("android.run.configuration.type.description");
  }

  public Icon getIcon() {
    return AndroidUtils.ANDROID_ICON;
  }

  @NotNull
  public String getId() {
    return "AndroidRunConfigurationType";
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  public ConfigurationFactory getFactory() {
    return myFactory;
  }
}

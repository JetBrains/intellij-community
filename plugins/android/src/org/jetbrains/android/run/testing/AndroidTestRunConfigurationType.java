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

package org.jetbrains.android.run.testing;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LayeredIcon;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 27, 2009
 * Time: 2:25:19 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidTestRunConfigurationType implements ConfigurationType {
  private static final Icon ANDROID_TEST_ICON;

  static {
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(AndroidIcons.Android, 0);
    icon.setIcon(AllIcons.Nodes.JunitTestMark, 1);
    ANDROID_TEST_ICON = icon;
  }

  private final ConfigurationFactory myFactory = new ConfigurationFactory(this) {
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new AndroidTestRunConfiguration("", project, this);
    }

    @Override
    public boolean canConfigurationBeSingleton() {
      return false;
    }
  };

  public static AndroidTestRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(AndroidTestRunConfigurationType.class);
  }

  public String getDisplayName() {
    return AndroidBundle.message("android.test.run.configuration.type.name");
  }

  public String getConfigurationTypeDescription() {
    return AndroidBundle.message("android.test.run.configuration.type.description");
  }

  public Icon getIcon() {
    return ANDROID_TEST_ICON;
  }

  @NotNull
  public String getId() {
    return "AndroidTestRunConfigurationType";
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  public ConfigurationFactory getFactory() {
    return myFactory;
  }
}

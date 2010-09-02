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
package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.project.Project;

/**
 * @author max
 */
public class ProjectConfigurablesGroup extends ConfigurablesGroupBase implements ConfigurableGroup {
  private final Project myProject;

  public ProjectConfigurablesGroup(Project project) {
    super(project, ConfigurableExtensionPointUtil.PROJECT_CONFIGURABLES);
    myProject = project;
  }

  public String getDisplayName() {
    if (isDefault()) return OptionsBundle.message("template.project.settings.display.name");
    return OptionsBundle.message("project.settings.display.name", myProject.getName());
  }

  public String getShortName() {
    return isDefault() ? OptionsBundle.message("template.project.settings.short.name") : OptionsBundle
      .message("project.settings.short.name");
  }

  private boolean isDefault() {
    return myProject.isDefault();
  }

  @Override
  protected ConfigurableFilter getConfigurableFilter() {
    return new ConfigurableFilter() {
      public boolean isIncluded(final Configurable configurable) {
        if (isDefault() && configurable instanceof NonDefaultProjectConfigurable) return false;
        return true;
      }
    };
  }

  public int hashCode() {
    return 0;
  }

  public boolean equals(Object object) {
    return object instanceof ProjectConfigurablesGroup && ((ProjectConfigurablesGroup)object).myProject == myProject;
  }
}

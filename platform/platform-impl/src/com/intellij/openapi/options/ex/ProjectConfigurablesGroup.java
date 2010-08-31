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

import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;

import java.util.List;

/**
 * @author max
 */
public class ProjectConfigurablesGroup implements ConfigurableGroup {
  private Project myProject;

  public ProjectConfigurablesGroup(Project project) {
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

  public Configurable[] getConfigurables() {
    final ConfigurableEP[] extensions = myProject.getExtensions(ConfigurableExtensionPointUtil.PROJECT_CONFIGURABLES);
    Configurable[] components = myProject.getComponents(Configurable.class);
    List<Configurable> result = ConfigurableExtensionPointUtil.buildConfigurablesList(extensions, components, new ConfigurableFilter() {
      public boolean isIncluded(final Configurable configurable) {
        if (isDefault() && configurable instanceof NonDefaultProjectConfigurable) return false;
        return true;
      }
    });

    return result.toArray(new Configurable[result.size()]);
  }

  public int hashCode() {
    return 0;
  }

  public boolean equals(Object object) {
    return object instanceof ProjectConfigurablesGroup && ((ProjectConfigurablesGroup)object).myProject == myProject;
  }
}

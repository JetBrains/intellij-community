// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 * @deprecated ShowSettingsUtilImpl#getConfigurableGroups(project, false)
 */
@Deprecated
public class ProjectConfigurablesGroup extends ConfigurablesGroupBase implements ConfigurableGroup {
  private final Project myProject;

  public ProjectConfigurablesGroup(@NotNull Project project) {
    super(project, Configurable.PROJECT_CONFIGURABLE);
    myProject = project;
  }

  @Override
  public String getDisplayName() {
    if (isDefault()) return OptionsBundle.message("template.project.settings.display.name");
    return OptionsBundle.message("project.settings.display.name", myProject.getName());
  }

  private boolean isDefault() {
    return myProject.isDefault();
  }

  @Override
  public ConfigurableFilter getConfigurableFilter() {
    return new ConfigurableFilter() {
      @Override
      public boolean isIncluded(final Configurable configurable) {
        return !isDefault() || !ConfigurableWrapper.isNonDefaultProject(configurable);
      }
    };
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public boolean equals(Object object) {
    return object instanceof ProjectConfigurablesGroup && ((ProjectConfigurablesGroup)object).myProject == myProject;
  }
}

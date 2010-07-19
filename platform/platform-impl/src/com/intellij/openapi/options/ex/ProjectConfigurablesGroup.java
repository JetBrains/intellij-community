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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author max
 */
public class ProjectConfigurablesGroup implements ConfigurableGroup {
  private Project myProject;
  private boolean myIncludeProjectStructure;
  private static final String PROJECT_STRUCTURE_CLASS_FQ_NAME = "com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable";

  public ProjectConfigurablesGroup(Project project) {
    this(project, true);
  }

  public ProjectConfigurablesGroup(Project project, boolean includeProjectStructure) {
    myProject = project;
    myIncludeProjectStructure = includeProjectStructure;
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
    return getConfigurables(myProject, new ConfigurableFilter() {
      public boolean isIncluded(final Configurable configurable) {
        if (isDefault() && configurable instanceof NonDefaultProjectConfigurable) return false;
        if (configurable instanceof Configurable.Assistant) return false;
        if (configurable instanceof OptionalConfigurable && !((OptionalConfigurable) configurable).needDisplay()) return false;
        if (!myIncludeProjectStructure && PROJECT_STRUCTURE_CLASS_FQ_NAME.equals(configurable.getClass().getName())) return false;
        return true;
      }
    });
  }

  private static Configurable[] getConfigurables(Project project, final ConfigurableFilter filter) {
    final Configurable[] extensions = project.getExtensions(Configurable.PROJECT_CONFIGURABLES);
    Configurable[] components = project.getComponents(Configurable.class);
    List<Configurable> result = buildConfigurablesList(extensions, components, filter);

    return result.toArray(new Configurable[result.size()]);
  }

  @Nullable
  public static Configurable getProjectStructureConfigurable(Project project) {
    final Configurable[] configurables = getConfigurables(project, new ConfigurableFilter() {
      public boolean isIncluded(final Configurable configurable) {
        return PROJECT_STRUCTURE_CLASS_FQ_NAME.equals(configurable.getClass().getName());
      }
    });

    return configurables.length == 1 ? configurables[0] : null;
  }

  static List<Configurable> buildConfigurablesList(final Configurable[] extensions, final Configurable[] components, ConfigurableFilter filter) {
    List<Configurable> result = new ArrayList<Configurable>();
    ContainerUtil.addAll(result, extensions);
    ContainerUtil.addAll(result, components);

    final Iterator<Configurable> iterator = result.iterator();
    while (iterator.hasNext()) {
      Configurable each = iterator.next();
      if (!filter.isIncluded(each)) {
        iterator.remove();
      }
    }

    return result;
  }

  public int hashCode() {
    return 0;
  }

  public boolean equals(Object object) {
    return object instanceof ProjectConfigurablesGroup && ((ProjectConfigurablesGroup)object).myProject == myProject;
  }


}

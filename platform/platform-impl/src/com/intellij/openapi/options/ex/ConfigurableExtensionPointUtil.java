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
package com.intellij.openapi.options.ex;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.OptionalConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author nik
 */
public class ConfigurableExtensionPointUtil {
  public static final ExtensionPointName<ConfigurableEP> APPLICATION_CONFIGURABLES = ExtensionPointName.create("com.intellij.applicationConfigurable");
  public static final ExtensionPointName<ConfigurableEP> PROJECT_CONFIGURABLES = ExtensionPointName.create("com.intellij.projectConfigurable");

  private ConfigurableExtensionPointUtil() {
  }


  public static List<Configurable> buildConfigurablesList(final ConfigurableEP[] extensions, final Configurable[] components, ConfigurableFilter filter) {
    List<Configurable> result = new ArrayList<Configurable>();
    for (ConfigurableEP extension : extensions) {
      ContainerUtil.addIfNotNull(extension.createConfigurable(), result);
    }
    ContainerUtil.addAll(result, components);

    final Iterator<Configurable> iterator = result.iterator();
    while (iterator.hasNext()) {
      Configurable each = iterator.next();
      if (each instanceof Configurable.Assistant
          || each instanceof OptionalConfigurable && !((OptionalConfigurable) each).needDisplay()
          || filter != null && !filter.isIncluded(each)) {
        iterator.remove();
      }
    }

    return result;
  }

  @NotNull
  public static <T extends Configurable> T createProjectConfigurable(@NotNull Project project, @NotNull Class<T> configurableClass) {
    return createConfigurable(project.getExtensions(PROJECT_CONFIGURABLES), configurableClass);
  }

  @NotNull
  public static <T extends Configurable> T createApplicationConfigurable(@NotNull Class<T> configurableClass) {
    return createConfigurable(APPLICATION_CONFIGURABLES.getExtensions(), configurableClass);
  }

  @NotNull
  private static <T extends Configurable> T createConfigurable(ConfigurableEP[] extensions, Class<T> configurableClass) {
    final String className = configurableClass.getName();
    for (ConfigurableEP extension : extensions) {
      if (className.equals(extension.implementationClass) || className.equals(extension.instanceClass)) {
        return configurableClass.cast(extension.createConfigurable());
      }
    }
    throw new IllegalArgumentException("Cannot find configurable of " + configurableClass);
  }
}

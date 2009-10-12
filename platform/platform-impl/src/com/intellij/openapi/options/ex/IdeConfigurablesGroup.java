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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.OptionalConfigurable;
import com.intellij.openapi.options.OptionsBundle;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 9, 2003
 * Time: 3:35:56 PM
 * To change this template use Options | File Templates.
 */
public class IdeConfigurablesGroup implements ConfigurableGroup {
  public String getDisplayName() {
    return OptionsBundle.message("ide.settings.display.name");
  }

  public String getShortName() {
    return OptionsBundle.message("ide.settings.short.name");
  }

  public Configurable[] getConfigurables() {
    final Application app = ApplicationManager.getApplication();
    final Configurable[] extensions = app.getExtensions(Configurable.APPLICATION_CONFIGURABLES);
    Configurable[] components = app.getComponents(Configurable.class);

    List<Configurable> result = ProjectConfigurablesGroup.buildConfigurablesList(extensions, components, new ConfigurableFilter() {
      public boolean isIncluded(final Configurable configurable) {
        if (configurable instanceof Configurable.Assistant) return false;
        if (configurable instanceof OptionalConfigurable && !((OptionalConfigurable) configurable).needDisplay()) return false;
        return true;
      }
    });

    return result.toArray(new Configurable[result.size()]);
  }

  public boolean equals(Object object) {
    return object instanceof IdeConfigurablesGroup;
  }

  public int hashCode() {
    return 0;
  }
}

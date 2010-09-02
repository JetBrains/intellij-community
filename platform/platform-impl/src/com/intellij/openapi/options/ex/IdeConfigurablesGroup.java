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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.OptionsBundle;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 9, 2003
 * Time: 3:35:56 PM
 * To change this template use Options | File Templates.
 */
public class IdeConfigurablesGroup extends ConfigurablesGroupBase implements ConfigurableGroup {
  public IdeConfigurablesGroup() {
    super(ApplicationManager.getApplication(), ConfigurableExtensionPointUtil.APPLICATION_CONFIGURABLES);
  }

  public String getDisplayName() {
    return OptionsBundle.message("ide.settings.display.name");
  }

  public String getShortName() {
    return OptionsBundle.message("ide.settings.short.name");
  }

  @Override
  protected ConfigurableFilter getConfigurableFilter() {
    return null;
  }

  public boolean equals(Object object) {
    return object instanceof IdeConfigurablesGroup;
  }

  public int hashCode() {
    return 0;
  }
}

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

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.ConfigurableGroup;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public abstract class ConfigurablesGroupBase implements ConfigurableGroup {
  private Configurable[] myChildren;
  private ComponentManager myComponentManager;
  private final ExtensionPointName<ConfigurableEP> myConfigurablesExtensionPoint;

  protected ConfigurablesGroupBase(ComponentManager componentManager, final ExtensionPointName<ConfigurableEP> configurablesExtensionPoint) {
    myComponentManager = componentManager;
    myConfigurablesExtensionPoint = configurablesExtensionPoint;
  }

  @Override
  public Configurable[] getConfigurables() {
    if (myChildren == null) {
      final ConfigurableEP[] extensions = myComponentManager.getExtensions(myConfigurablesExtensionPoint);
      Configurable[] components = myComponentManager.getComponents(Configurable.class);

      List<Configurable> result = ConfigurableExtensionPointUtil.buildConfigurablesList(extensions, components, getConfigurableFilter());
      myChildren = result.toArray(new Configurable[result.size()]);
    }
    return myChildren;
  }

  @Nullable
  protected abstract ConfigurableFilter getConfigurableFilter();
}

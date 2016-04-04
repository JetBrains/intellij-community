/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
@Deprecated
public abstract class ConfigurablesGroupBase implements ConfigurableGroup {
  private Configurable[] myChildren;
  private final ComponentManager myComponentManager;
  private final ExtensionPointName<ConfigurableEP<Configurable>> myConfigurablesExtensionPoint;

  protected ConfigurablesGroupBase(ComponentManager componentManager, final ExtensionPointName<ConfigurableEP<Configurable>> configurablesExtensionPoint) {
    myComponentManager = componentManager;
    myConfigurablesExtensionPoint = configurablesExtensionPoint;
  }

  @Override
  public Configurable[] getConfigurables() {
    if (myChildren == null) {
      if (myComponentManager.isDisposed()) {
        return new Configurable[0];
      }

      ConfigurableEP<Configurable>[] extensions = myComponentManager.getExtensions(myConfigurablesExtensionPoint);
      List<Configurable> result = ConfigurableExtensionPointUtil.buildConfigurablesList(extensions, getConfigurableFilter());
      myChildren = result.toArray(new Configurable[result.size()]);
    }
    return myChildren;
  }

  @Nullable
  protected abstract ConfigurableFilter getConfigurableFilter();

  @Override
  public String getShortName() {
    return null;
  }
}

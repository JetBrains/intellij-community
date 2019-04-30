// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.ConfigurableGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
@Deprecated
abstract class ConfigurablesGroupBase implements ConfigurableGroup {
  private Configurable[] myChildren;
  private final ComponentManager myComponentManager;
  private final ExtensionPointName<ConfigurableEP<Configurable>> myConfigurablesExtensionPoint;

  protected ConfigurablesGroupBase(ComponentManager componentManager, @NotNull ExtensionPointName<ConfigurableEP<Configurable>> configurablesExtensionPoint) {
    myComponentManager = componentManager;
    myConfigurablesExtensionPoint = configurablesExtensionPoint;
  }

  @NotNull
  @Override
  public Configurable[] getConfigurables() {
    if (myChildren == null) {
      if (myComponentManager.isDisposed()) {
        return new Configurable[0];
      }

      ConfigurableEP<Configurable>[] extensions = myComponentManager.getExtensions(myConfigurablesExtensionPoint);
      List<Configurable> result = ConfigurableExtensionPointUtil.buildConfigurablesList(extensions, getConfigurableFilter());
      myChildren = result.toArray(new Configurable[0]);
    }
    return myChildren;
  }

  @Nullable
  protected abstract ConfigurableFilter getConfigurableFilter();

}

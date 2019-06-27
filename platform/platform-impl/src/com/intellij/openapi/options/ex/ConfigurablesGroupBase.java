// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.ConfigurableGroup;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 * @deprecated needed for {@link IdeConfigurablesGroup}
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
abstract class ConfigurablesGroupBase implements ConfigurableGroup {
  private Configurable[] myChildren;
  private final ExtensionPointName<ConfigurableEP<Configurable>> myConfigurablesExtensionPoint;

  protected ConfigurablesGroupBase(@NotNull ExtensionPointName<ConfigurableEP<Configurable>> configurablesExtensionPoint) {
    myConfigurablesExtensionPoint = configurablesExtensionPoint;
  }

  @NotNull
  @Override
  public Configurable[] getConfigurables() {
    if (myChildren == null) {
      if (ApplicationManager.getApplication().isDisposed()) {
        return new Configurable[0];
      }

      myChildren = ConfigurableExtensionPointUtil.buildConfigurablesList(myConfigurablesExtensionPoint.getExtensionList(), getConfigurableFilter())
        .toArray(new Configurable[0]);
    }
    return myChildren;
  }

  @Nullable
  protected abstract ConfigurableFilter getConfigurableFilter();

}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.OptionsBundle;
import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated ShowSettingsUtilImpl#getConfigurableGroups(null, true)
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
public class IdeConfigurablesGroup extends ConfigurablesGroupBase implements ConfigurableGroup {
  public IdeConfigurablesGroup() {
    super(Configurable.APPLICATION_CONFIGURABLE);
  }

  @Override
  public String getDisplayName() {
    return OptionsBundle.message("ide.settings.display.name");
  }

  @Override
  public ConfigurableFilter getConfigurableFilter() {
    return null;
  }

  @Override
  public boolean equals(Object object) {
    return object instanceof IdeConfigurablesGroup;
  }

  @Override
  public int hashCode() {
    return 0;
  }
}

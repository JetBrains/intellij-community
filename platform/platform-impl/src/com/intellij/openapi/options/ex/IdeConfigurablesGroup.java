// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.OptionsBundle;

/**
 * @deprecated ShowSettingsUtilImpl#getConfigurableGroups(null, true)
 */
@Deprecated
public class IdeConfigurablesGroup extends ConfigurablesGroupBase implements ConfigurableGroup {
  public IdeConfigurablesGroup() {
    super(ApplicationManager.getApplication(), Configurable.APPLICATION_CONFIGURABLE);
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

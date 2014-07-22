/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class XDebuggerSettingsPanelProviderImpl extends DebuggerSettingsPanelProvider {
  @NotNull
  @Override
  public Collection<? extends Configurable> getConfigurables() {
    List<Configurable> list = new SmartList<Configurable>();
    for (XDebuggerSettings settings : XDebuggerSettingsManager.getInstanceImpl().getSettingsList()) {
      ContainerUtil.addIfNotNull(list, settings.createConfigurable());
    }
    return list;
  }

  @NotNull
  @Override
  public Collection<? extends Configurable> getConfigurable(@NotNull XDebuggerSettings.Category category) {
    List<Configurable> list;
    if (category == XDebuggerSettings.Category.ROOT) {
      list = new SmartList<Configurable>(new GeneralConfigurable());
    }
    else {
      list = null;
    }

    for (XDebuggerSettings settings : XDebuggerSettingsManager.getInstanceImpl().getSettingsList()) {
      Configurable configurable = settings.createConfigurable(category);
      if (configurable != null) {
        if (list == null) {
          list = new SmartList<Configurable>();
        }
        list.add(configurable);
      }
    }
    return ContainerUtil.notNullize(list);
  }

  @Override
  public void generalApplied(@NotNull XDebuggerSettings.Category category) {
    for (XDebuggerSettings settings : XDebuggerSettingsManager.getInstanceImpl().getSettingsList()) {
      settings.generalApplied(category);
    }
  }
}

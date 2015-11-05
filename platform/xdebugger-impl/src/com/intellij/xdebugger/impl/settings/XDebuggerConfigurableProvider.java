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
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SimpleConfigurable;
import com.intellij.openapi.util.Getter;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.settings.DebuggerConfigurableProvider;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

class XDebuggerConfigurableProvider extends DebuggerConfigurableProvider {
  @NotNull
  @Override
  public Collection<? extends Configurable> getConfigurables(@NotNull DebuggerSettingsCategory category) {
    List<Configurable> list;
    if (category == DebuggerSettingsCategory.GENERAL) {
      list = new SmartList<Configurable>(SimpleConfigurable.create("debugger.general", "", GeneralConfigurableUi.class, new Getter<XDebuggerGeneralSettings>() {
        @Override
        public XDebuggerGeneralSettings get() {
          return XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings();
        }
      }));
    }
    else {
      list = null;
    }

    for (XDebuggerSettings<?> settings : XDebuggerSettingManagerImpl.getInstanceImpl().getSettingsList()) {
      Collection<? extends Configurable> configurables = settings.createConfigurables(category);
      if (!configurables.isEmpty()) {
        if (list == null) {
          list = new SmartList<Configurable>();
        }
        list.addAll(configurables);
      }
    }

    if (category == DebuggerSettingsCategory.ROOT) {
      for (XDebuggerSettings settings : XDebuggerSettingManagerImpl.getInstanceImpl().getSettingsList()) {
        @SuppressWarnings("deprecation")
        Configurable configurable = settings.createConfigurable();
        if (configurable != null) {
          if (list == null) {
            list = new SmartList<Configurable>();
          }
          list.add(configurable);
        }
      }
    }
    return ContainerUtil.notNullize(list);
  }

  @Override
  public void generalApplied(@NotNull DebuggerSettingsCategory category) {
    for (XDebuggerSettings<?> settings : XDebuggerSettingManagerImpl.getInstanceImpl().getSettingsList()) {
      settings.generalApplied(category);
    }
  }

  @Override
  public boolean isTargetedToProduct(@NotNull Configurable configurable) {
    for (XDebuggerSettings<?> settings : XDebuggerSettingManagerImpl.getInstanceImpl().getSettingsList()) {
      if (settings.isTargetedToProduct(configurable)) {
        return true;
      }
    }
    return super.isTargetedToProduct(configurable);
  }
}
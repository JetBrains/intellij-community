// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.settings.DebuggerConfigurableProvider;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

final class XDebuggerConfigurableProvider extends DebuggerConfigurableProvider {
  @Override
  public @NotNull Collection<? extends Configurable> getConfigurables(@NotNull DebuggerSettingsCategory category) {
    List<Configurable> list = new SmartList<>();
    if (category == DebuggerSettingsCategory.GENERAL) {
      list.add(new DebuggerGeneralConfigurable());
    }

    XDebuggerSettingManagerImpl.getInstanceImpl().forEachSettings(settings -> {
      //noinspection unchecked
      list.addAll(settings.createConfigurables(category));
    });

    if (category == DebuggerSettingsCategory.ROOT) {
      XDebuggerSettingManagerImpl.getInstanceImpl().forEachSettings(settings -> {
        Configurable configurable = settings.createConfigurable();
        if (configurable != null) {
          list.add(configurable);
        }
      });
    }
    return list;
  }

  @Override
  public void generalApplied(@NotNull DebuggerSettingsCategory category) {
    XDebuggerSettingManagerImpl.getInstanceImpl().forEachSettings(settings -> settings.generalApplied(category));
  }

  @Override
  public boolean isTargetedToProduct(@NotNull Configurable configurable) {
    if (XDebuggerSettingManagerImpl.getInstanceImpl().findFirstSettings(settings -> settings.isTargetedToProduct(configurable)) != null) {
        return true;
    }
    return super.isTargetedToProduct(configurable);
  }
}
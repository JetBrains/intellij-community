// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SimpleConfigurable;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.settings.DebuggerConfigurableProvider;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

final class XDebuggerConfigurableProvider extends DebuggerConfigurableProvider {
  @NotNull
  @Override
  public Collection<? extends Configurable> getConfigurables(@NotNull DebuggerSettingsCategory category) {
    List<Configurable> list = new SmartList<>();
    if (category == DebuggerSettingsCategory.GENERAL) {
      list.add(SimpleConfigurable.create("debugger.general", "", GeneralConfigurableUi.class,
                                         () -> XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings()));
    }

    XDebuggerSettings.EXTENSION_POINT.forEachExtensionSafe(settings -> {
      //noinspection unchecked
      list.addAll(settings.createConfigurables(category));
    });

    if (category == DebuggerSettingsCategory.ROOT) {
      XDebuggerSettings.EXTENSION_POINT.forEachExtensionSafe(settings -> {
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
    XDebuggerSettings.EXTENSION_POINT.forEachExtensionSafe(settings -> settings.generalApplied(category));
  }

  @Override
  public boolean isTargetedToProduct(@NotNull Configurable configurable) {
    if (XDebuggerSettings.EXTENSION_POINT.findFirstSafe(settings -> settings.isTargetedToProduct(configurable)) != null) {
        return true;
    }
    return super.isTargetedToProduct(configurable);
  }
}
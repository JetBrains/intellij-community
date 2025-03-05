// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.settings;

import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class DataViewsConfigurable extends SubCompositeConfigurable {
  @Override
  public @NotNull String getId() {
    return "debugger.dataViews";
  }

  @Override
  public @Nls String getDisplayName() {
    return XDebuggerBundle.message("debugger.dataViews.display.name");
  }

  @Override
  protected DataViewsConfigurableUi createRootUi() {
    return new DataViewsConfigurableUi();
  }

  @Override
  protected @NotNull DebuggerSettingsCategory getCategory() {
    return DebuggerSettingsCategory.DATA_VIEWS;
  }

  @Override
  public @Nullable String getHelpTopic() {
    return "reference.idesettings.debugger.dataviews";
  }

  @Override
  protected @NotNull XDebuggerDataViewSettings getSettings() {
    return XDebuggerSettingManagerImpl.getInstanceImpl().getDataViewSettings();
  }
}
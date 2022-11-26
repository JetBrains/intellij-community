// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;

final class PositionPanelWidgetFactory extends StatusBarEditorBasedWidgetFactory {
  @Override
  public @NotNull String getId() {
    return StatusBar.StandardWidgets.POSITION_PANEL;
  }

  @Override
  public @NotNull String getDisplayName() {
    return UIBundle.message("status.bar.position.widget.name");
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
    return new PositionPanel(project);
  }
}

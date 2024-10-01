// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory;
import com.intellij.ui.UIBundle;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
@ApiStatus.NonExtendable
public class LineSeparatorWidgetFactory extends StatusBarEditorBasedWidgetFactory {
  @Override
  public @NotNull String getId() {
    return StatusBar.StandardWidgets.LINE_SEPARATOR_PANEL;
  }

  @Override
  public @NotNull String getDisplayName() {
    return UIBundle.message("status.bar.line.separator.widget.name");
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project, @NotNull CoroutineScope scope) {
    return new LineSeparatorPanel(project, scope);
  }
}

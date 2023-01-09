// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WidgetPresentation;
import com.intellij.openapi.wm.WidgetPresentationDataContext;
import com.intellij.openapi.wm.WidgetPresentationFactory;
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory;
import com.intellij.ui.UIBundle;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;

final class ReadOnlyAttributeWidgetFactory extends StatusBarEditorBasedWidgetFactory implements WidgetPresentationFactory {
  @Override
  public @NotNull String getId() {
    return StatusBar.StandardWidgets.READONLY_ATTRIBUTE_PANEL;
  }

  @Override
  public @NotNull String getDisplayName() {
    return UIBundle.message("status.bar.read.only.widget.name");
  }

  @Override
  public @NotNull WidgetPresentation createPresentation(@NotNull WidgetPresentationDataContext context, @NotNull CoroutineScope scope) {
    return new ToggleReadOnlyAttributePanel(context, scope);
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.picker.ColorListener;
import com.intellij.ui.picker.ColorPickerPopupCloseListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;

/**
 * Shows the color picker popup.
 * The module {@code intellij.platform.ide.colorPicker} registers the implementation as an application service.
 * Without the module, {@link ColorPicker} shows the color picker dialog.
 */
@ApiStatus.Internal
public interface ColorPickerPopupProvider {
  /**
   * @param location the point of the popup, or {@code null} for the mouse pointer location
   */
  void showPopup(@Nullable Project project,
                 @Nullable Color currentColor,
                 @NotNull ColorListener listener,
                 @Nullable RelativePoint location,
                 boolean showAlpha,
                 boolean showAlphaAsPercent,
                 @Nullable ColorPickerPopupCloseListener popupCloseListener);
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
public class MenuItemPresentationFactory extends PresentationFactory {
  public static final @NotNull Key<Object> HIDE_ICON = Key.create("HIDE_ICON");

  private final boolean forceHideIcon;

  public MenuItemPresentationFactory() {
    forceHideIcon = false;
  }

  public MenuItemPresentationFactory(boolean forceHideIcon) {
    this.forceHideIcon = forceHideIcon;
  }

  @Override
  protected void processPresentation(@NotNull AnAction action, @NotNull Presentation presentation) {
    if (forceHideIcon || !UISettings.getInstance().getShowIconsInMenus()) {
      presentation.setIcon(null);
      presentation.setDisabledIcon(null);
      presentation.setHoveredIcon(null);
      presentation.putClientProperty(HIDE_ICON, Boolean.TRUE);
    }
  }

  @Override
  protected void processPresentation(@NotNull Presentation presentation) {
    super.processPresentation(presentation);
    if (forceHideIcon || !UISettings.getInstance().getShowIconsInMenus()) {
      presentation.setIcon(null);
      presentation.setDisabledIcon(null);
      presentation.setHoveredIcon(null);
      presentation.putClientProperty(HIDE_ICON, Boolean.TRUE);
    }
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.DynamicBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.actionSystem.Presentation.STRIP_MNEMONIC;

/**
 * @author Roman.Chernyatchik
 */
public class MenuItemPresentationFactory extends PresentationFactory {
  public static final String HIDE_ICON = "HIDE_ICON";

  private final boolean myForceHide;

  public MenuItemPresentationFactory() {
    this(false);
  }

  public MenuItemPresentationFactory(boolean forceHide) {
    myForceHide = forceHide;
  }

  public boolean shallHideIcons() {
    return myForceHide || !UISettings.getInstance().getShowIconsInMenus();
  }

  private static final @NotNull NotNullLazyValue<Boolean> hasAnyLanguagePack =
    NotNullLazyValue.createValue(DynamicBundle.LanguageBundleEP.EP_NAME::hasAnyExtensions);

  @Override
  protected void processPresentation(Presentation presentation) {
    if (SystemInfo.isMac && hasAnyLanguagePack.getValue()) {
      presentation.putClientProperty(STRIP_MNEMONIC, Boolean.TRUE);
    }

    if (shallHideIcons()) {
      presentation.setIcon(null);
      presentation.setDisabledIcon(null);
      presentation.setHoveredIcon(null);
      presentation.putClientProperty(HIDE_ICON, Boolean.TRUE);
    }
  }
}

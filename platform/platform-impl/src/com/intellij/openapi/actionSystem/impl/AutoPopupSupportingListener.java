// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import org.jetbrains.annotations.NotNull;

/**
 * This class registers a provided popup with ActionManager when the popup is shown,
 * and unregisters when it is hidden, thus allowing it to be used from an action toolbar in auto-popup mode.
 * Use {@link AutoPopupSupportingListener#installOn(JBPopup)} to install the listener.
 */
public class AutoPopupSupportingListener implements JBPopupListener {
  private final @NotNull JBPopup myPopup;

  public AutoPopupSupportingListener(@NotNull JBPopup popup) {
    myPopup = popup;
  }

  @Override
  public void beforeShown(@NotNull LightweightWindowEvent event) {
    ((ActionManagerImpl)ActionManager.getInstance()).addActionPopup(myPopup);
  }

  @Override
  public void onClosed(@NotNull LightweightWindowEvent event) {
    ((ActionManagerImpl)ActionManager.getInstance()).removeActionPopup(myPopup);
  }

  /**
   * This method allows a popup to be used from an action toolbar in auto-popup mode,
   * by registering it with ActionManager so the auto-popup won't close on mouse exit.
   *
   * @param popup target popup
   * @see ActionToolbarImpl#showAutoPopup()
   */
  public static void installOn(@NotNull JBPopup popup) {
    popup.addListener(new AutoPopupSupportingListener(popup));
  }
}

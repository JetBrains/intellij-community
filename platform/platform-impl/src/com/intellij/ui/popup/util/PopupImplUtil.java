// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.util;

import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.reference.SoftReference;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.lang.ref.WeakReference;

@ApiStatus.Internal
public final class PopupImplUtil {
  private static final Logger LOG = Logger.getInstance(PopupImplUtil.class);

  private static final String POPUP_TOGGLE_BUTTON = "POPUP_TOGGLE_BUTTON";

  private PopupImplUtil() { }

  public static AccessToken prohibitFocusEventsInHandleSelect() {
    boolean[] insideOnChosen = { true };
    //noinspection resource
    AccessToken token = ProhibitAWTEvents.startFiltered("Popup.handleSelect", e -> {
      if (!(e instanceof WindowEvent && e.getID() == WindowEvent.WINDOW_ACTIVATED && ((WindowEvent)e).getWindow() instanceof JDialog)) {
        return null;
      }
      Throwable throwable = new Throwable("Showing dialogs in PopupStep.onChosen can result in focus issues. " +
                                          "Please put the handler into BaseStep.doFinalStep or PopupStep.getFinalRunnable.\n  " + e);
      // give the secondary event loop in `actionSystem.impl.Utils.expandActionGroupImpl`
      // a chance to quit in case the focus event is created right inside `dispatchEvents` code
      ApplicationManager.getApplication().invokeLater(() -> {
        LOG.error(throwable);
      }, ModalityState.any(), __ -> !insideOnChosen[0]);
      return null;
    });
    return new AccessToken() {
      @Override
      public void finish() {
        insideOnChosen[0] = false;
        token.finish();
      }
    };
  }

  public static @Nullable Object getDataImplForList(@NotNull JList<?> list, @NotNull String dataId) {
    if (PlatformCoreDataKeys.SELECTED_ITEM.is(dataId)) {
      int index = list.getSelectedIndex();
      return index > -1 ? list.getSelectedValue() : ObjectUtils.NULL;
    }
    else if (PlatformCoreDataKeys.SELECTED_ITEMS.is(dataId)) {
      Object[] values = list.getSelectedValues();
      for (int i = 0; i < values.length; i++) {
        if (values[i] == null) {
          values[i] = ObjectUtils.NULL;
        }
      }
      return values;
    }
    return null;
  }

  /**
   * @param toggleButton treat this component as toggle button and block further mouse event processing
   *                     if user closed the popup by clicking on it
   */
  public static void setPopupToggleButton(@NotNull JBPopup jbPopup, @Nullable Component toggleButton) {
    JComponent content = jbPopup.getContent();
    content.putClientProperty(POPUP_TOGGLE_BUTTON, toggleButton != null ? new WeakReference<>(toggleButton) : null);
  }

  @Nullable
  public static Component getPopupToggleButton(@NotNull JBPopup jbPopup) {
    return (Component)SoftReference.dereference((WeakReference<?>)jbPopup.getContent().getClientProperty(POPUP_TOGGLE_BUTTON));
  }
}

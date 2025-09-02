// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.util;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.DimensionService;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;

@ApiStatus.Internal
public final class PopupImplUtil {
  private static final Logger LOG = Logger.getInstance(PopupImplUtil.class);

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

  public static void uiSnapshotForList(@NotNull JList<?> list, @NotNull DataSink sink) {
    uiSnapshotForList(list, sink, false);
  }

  public static void uiSnapshotForList(@NotNull JList<?> list, @NotNull DataSink sink, boolean lazy) {
    int index = list.getSelectedIndex();
    if (lazy) {
      sink.lazy(PlatformCoreDataKeys.SELECTED_ITEM, () -> index > -1 ? list.getSelectedValue() : ObjectUtils.NULL);
    }
    else {
      sink.set(PlatformCoreDataKeys.SELECTED_ITEM, index > -1 ? list.getSelectedValue() : ObjectUtils.NULL);
    }

    Object[] values = list.getSelectedValues();
    for (int i = 0; i < values.length; i++) {
      if (values[i] == null) {
        values[i] = ObjectUtils.NULL;
      }
    }
    if (lazy) {
      sink.lazy(PlatformCoreDataKeys.SELECTED_ITEMS, () -> values);
    }
    else {
      sink.set(PlatformCoreDataKeys.SELECTED_ITEMS, values);
    }
  }

  public static @Nullable Component getClickSourceFromLastInputEvent() {
    var event = IdeEventQueue.getInstance().getTrueCurrentEvent();
    if (event instanceof MouseEvent mouseEvent) {
      return SwingUtilities.getDeepestComponentAt(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
    }
    if (event instanceof KeyEvent keyEvent) {
      return keyEvent.getComponent();
    }
    return null;
  }

  public static @NotNull Dimension getPopupSize(@NotNull JBPopup popup) {
    Dimension size = null;
    if (popup instanceof AbstractPopup) {
      final String dimensionKey = ((AbstractPopup)popup).getDimensionServiceKey();
      if (dimensionKey != null) {
        size = DimensionService.getInstance().getSize(dimensionKey);
      }
    }

    if (size == null) {
      size = popup.getContent().getPreferredSize();
    }

    return size;
  }
}

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.util;

import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusEvent;

@ApiStatus.Internal
public final class PopupImplUtil {
  private static final Logger LOG = Logger.getInstance(PopupImplUtil.class);

  private PopupImplUtil() { }

  public static AccessToken prohibitFocusEventsInHandleSelect() {
    boolean[] insideOnChosen = { true };
    //noinspection resource
    AccessToken token = ProhibitAWTEvents.startFiltered("Popup.handleSelect", e -> {
      if (!(e instanceof FocusEvent) || ((FocusEvent)e).isTemporary()) return null;
      Throwable throwable = new Throwable("Focus events are prohibited inside Popup.handleSelect; got " + e +
                                          "Please put the handler into BaseStep.doFinalStep or PopupStep.getFinalRunnable.");
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
    if (PlatformDataKeys.SELECTED_ITEM.is(dataId)) {
      int index = list.getSelectedIndex();
      return index > -1 ? list.getSelectedValue() : ObjectUtils.NULL;
    }
    else if (PlatformDataKeys.SELECTED_ITEMS.is(dataId)) {
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
}

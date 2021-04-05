// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.util;

import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;

import java.awt.event.FocusEvent;

public final class PopupImplUtil {
  private static final Logger LOG = Logger.getInstance(PopupImplUtil.class);

  public static AccessToken prohibitFocusEventsInHandleSelect() {
    boolean[] insideOnChosen = { true };
    //noinspection resource
    AccessToken token = ProhibitAWTEvents.startFiltered("Popup.handleSelect", e -> {
      if (!(e instanceof FocusEvent)) return null;
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
}

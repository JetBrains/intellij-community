// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.util;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.popup.ActionPopupStep;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PopupImplUtil {
  private static final Logger LOG = Logger.getInstance(PopupImplUtil.class);

  public static AccessToken prohibitDialogsInside(@NotNull PopupStep<?> step) {
    // todo try using AsyncPopupStep in ActionPopupStep#onChosen
    if (step instanceof ActionPopupStep &&
        Registry.is("actionSystem.update.actions.async") &&
        Registry.is("actionSystem.update.actions.async.ui")) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }
    AtomicBoolean insideOnChosen = new AtomicBoolean(true);
    ApplicationManager.getApplication().invokeLater(() -> {
      if (insideOnChosen.get()) {
        LOG.error("Showing dialogs from popup onChosen can result in focus issues. " +
                  "Please put the handler into BaseStep.doFinalStep or PopupStep.getFinalRunnable.");
      }
    }, ModalityState.any());
    return new AccessToken() {
      @Override
      public void finish() {
        insideOnChosen.set(false);
      }
    };
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

/*
 * @author denis
 */

import com.apple.eawt.event.GestureListener;
import com.apple.eawt.event.GestureUtilities;
import com.apple.eawt.event.PressureEvent;
import com.apple.eawt.event.PressureListener;
import com.intellij.openapi.actionSystem.PressureShortcut;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.impl.ui.MouseShortcutPanel;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class MacGestureSupportForMouseShortcutPanel {
  public MacGestureSupportForMouseShortcutPanel(MouseShortcutPanel panel, Runnable runnable) {
    try {
      GestureListener pressureListener = new PressureListener() {
        @Override
        public void pressure(PressureEvent e) {
          if (e.getStage() == 2) {
            panel.setShortcut(new PressureShortcut(e.getStage()));
            runnable.run();
          }
        }
      };
      GestureUtilities.addGestureListenerTo(panel, pressureListener);
    } catch (Throwable t) {
      Logger.getInstance(MacGestureSupportForMouseShortcutPanel.class).warn("macOS gesture support failed", t);
    }
  }
}

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
import com.intellij.openapi.keymap.impl.ui.MouseShortcutPanel;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.lang.JavaVersion;

public final class MacGestureSupportForMouseShortcutPanel {
  public MacGestureSupportForMouseShortcutPanel(MouseShortcutPanel panel, Runnable runnable) {
    //todo[kb]: return pressure listener to jbr17
    if (SystemInfo.isJetBrainsJvm && JavaVersion.current().isAtLeast(17)) {
      return;
    }
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
  }
}

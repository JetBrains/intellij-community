/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

public class MacGestureSupportForMouseShortcutPanel {
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

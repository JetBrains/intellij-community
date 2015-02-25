/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;

import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.ui.mac.foundation.Foundation.invoke;

/**
 * @author Denis Fokin
 */
public class JDK7WindowReorderingWorkaround {

  private static AtomicInteger requestorCount = new AtomicInteger();

  private static final Callback windowDidBecomeMainCallback = new Callback() {
    @SuppressWarnings("UnusedDeclaration") // this is a native up-call
    public void callback(ID self,
                         ID nsNotification)
    {
      if (requestorCount.intValue() == 0) {
        invoke(self, "oldWindowDidBecomeMain:", nsNotification);
      }
    }
  };

  private static final Callback canBecomeMainWindowCallback = new Callback() {
    @SuppressWarnings("UnusedDeclaration") // this is a native up-call
    public void callback(ID self)
    {
      if (requestorCount.intValue() == 0) {
        invoke(self, "oldCanBecomeMainWindow");
      }
    }
  };

  static {
    if (SystemInfo.isJavaVersionAtLeast("1.7")) {
      ID awtWindow = Foundation.getObjcClass("AWTWindow");

      Pointer windowDidBecomeMainMethod = Foundation.createSelector("windowDidBecomeMain:");
      ID originalWindowDidBecomeMain = Foundation.class_replaceMethod(awtWindow, windowDidBecomeMainMethod,
                                                                            windowDidBecomeMainCallback, "v@::@");

      Foundation.addMethodByID(awtWindow, Foundation.createSelector("oldWindowDidBecomeMain:"),
                               originalWindowDidBecomeMain, "v@::@");

      if (SystemInfo.isJavaVersionAtLeast("1.8")) {
        Pointer canBecomeMainWindowMethod = Foundation.createSelector("canBecomeMainWindow");
        ID originalCanBecomeMainWindow = Foundation.class_replaceMethod(awtWindow, canBecomeMainWindowMethod,
                                                                        canBecomeMainWindowCallback, "v@B");

        Foundation.addMethodByID(awtWindow, Foundation.createSelector("oldCanBecomeMainWindow"),
                                 originalCanBecomeMainWindow, "v@B");
      }
    }
  }

  static void disableReordering() {
    if (SystemInfo.isJavaVersionAtLeast("1.7")) {
      requestorCount.incrementAndGet();
    }
  }

  static void enableReordering () {
    if (SystemInfo.isJavaVersionAtLeast("1.7")) {
      requestorCount.decrementAndGet();
    }
  }

}

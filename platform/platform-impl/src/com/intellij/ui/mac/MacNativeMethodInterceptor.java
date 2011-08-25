/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility class to debug native method calls
 * 
 * @author pegov
 */
public class MacNativeMethodInterceptor {
  public static AtomicBoolean FIX_ENABLED = new AtomicBoolean(false);
  
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.mac.FindHeavyweightUnderCursorFix");
  
  private static final Callback CB = new Callback() {
    public void callback(ID self,
                         Pointer originalSelector,
                         Pointer selToPerform,
                         ID anObject,
                         ID withObject,
                         boolean waitUntilDone,
                         ID awtMode) {
      String selectorName = Foundation.stringFromSelector(selToPerform);
      //LOG.info("selectorName = " + selectorName);
      if (FIX_ENABLED.get() && "findHeavyweightUnderCursor:".equals(selectorName)) {
        return;
      }

      Foundation.invoke(self, "oldPerformOnMainThread:onObject:withObject:waitUntilDone:awtMode:", selToPerform, anObject, withObject, waitUntilDone, awtMode);
    }
  };

  static {
    if (SystemInfo.isMac) {
      ID threadUtilities = Foundation.getMetaClass("ThreadUtilities");
      ID originalMethod = Foundation.class_replaceMethod(threadUtilities,
                                                      Foundation
                                                        .createSelector("performOnMainThread:onObject:withObject:waitUntilDone:awtMode:"),
                                                      CB, "v@::@@B*");
      
      if (!Foundation.addMethodByID(threadUtilities,
                                    Foundation.createSelector("oldPerformOnMainThread:onObject:withObject:waitUntilDone:awtMode:"), originalMethod,
                                    "v@::@@B*")) {
        LOG.error("Unable to restore original behavior :(");
      }
    }
  }

  private MacNativeMethodInterceptor() {
  }
}

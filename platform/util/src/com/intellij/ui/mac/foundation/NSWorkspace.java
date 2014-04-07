/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ui.mac.foundation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NSWorkspace {
  @Nullable
  public static String absolutePathForAppBundleWithIdentifier(@NotNull String bundleID) {
    Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      ID workspace = Foundation.invoke(Foundation.getObjcClass("NSWorkspace"), "sharedWorkspace");
      return Foundation.toStringViaUTF8(Foundation.invoke(workspace, "absolutePathForAppBundleWithIdentifier:", 
                                                          Foundation.nsString(bundleID)));
    }
    finally {
      pool.drain();
    }
  }
}

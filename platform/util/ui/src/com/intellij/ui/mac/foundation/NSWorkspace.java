// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.foundation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.ui.mac.foundation.Foundation.*;

public final class NSWorkspace {
  @Nullable
  public static String absolutePathForAppBundleWithIdentifier(@NotNull String bundleID) {
    NSAutoreleasePool pool = new NSAutoreleasePool();
    try {
      ID workspace = getInstance();
      return toStringViaUTF8(invoke(workspace, "absolutePathForAppBundleWithIdentifier:",
                                                          nsString(bundleID)));
    }
    finally {
      pool.drain();
    }
  }

  @NotNull
  public static ID getInstance() {
    return invoke(getObjcClass("NSWorkspace"), "sharedWorkspace");
  }
}

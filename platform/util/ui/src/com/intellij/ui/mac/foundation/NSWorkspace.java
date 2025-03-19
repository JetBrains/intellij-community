// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.foundation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.ui.mac.foundation.Foundation.*;

public final class NSWorkspace {
  public static @Nullable String absolutePathForAppBundleWithIdentifier(@NotNull String bundleID) {
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

  public static @NotNull ID getInstance() {
    return invoke(getObjcClass("NSWorkspace"), "sharedWorkspace");
  }
}

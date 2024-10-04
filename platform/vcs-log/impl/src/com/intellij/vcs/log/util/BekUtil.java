// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.util;

import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class BekUtil {
  public static boolean isBekEnabled() { // todo drop later
    return !Registry.is("vcs.log.bek.sort.disabled");
  }

  public static boolean isLinearBekEnabled() {
    return isBekEnabled() && Registry.is("vcs.log.linear.bek.sort");
  }
}

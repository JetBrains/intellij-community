// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.devkit;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.jps.api.JpsDynamicBundle;

public class DevKitJpsBundle extends JpsDynamicBundle {
  @NonNls
  private static final String BUNDLE = "messages.DevKitJpsBundle";
  private static final DevKitJpsBundle INSTANCE = new DevKitJpsBundle();

  private DevKitJpsBundle() { super(BUNDLE); }

  public static @Nls @NotNull String message(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

}

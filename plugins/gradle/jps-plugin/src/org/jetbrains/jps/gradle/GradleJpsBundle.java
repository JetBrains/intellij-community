// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.gradle;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.jps.api.JpsDynamicBundle;

public final class GradleJpsBundle {
  private static final @NonNls String BUNDLE = "messages.GradleJpsBundle";
  private static final JpsDynamicBundle INSTANCE = new JpsDynamicBundle(GradleJpsBundle.class, BUNDLE);

  private GradleJpsBundle() {
  }

  public static @Nls @NotNull String message(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }
}

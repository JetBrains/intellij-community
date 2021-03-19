// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.appengine.build;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.jps.api.JpsDynamicBundle;

import java.util.function.Supplier;

public class JavaGoogleAppEngineJpsBundle extends JpsDynamicBundle {
  @NonNls private static final String BUNDLE = "messages.JavaGoogleAppEngineJpsBundle";
  private static final JavaGoogleAppEngineJpsBundle INSTANCE = new JavaGoogleAppEngineJpsBundle();

  private JavaGoogleAppEngineJpsBundle() {
    super(BUNDLE);
  }

  @NotNull
  @Nls
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  @NotNull
  public static Supplier<@Nls @NotNull String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                     Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}

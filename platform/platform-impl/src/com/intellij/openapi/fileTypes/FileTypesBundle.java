// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

@ApiStatus.Internal
public final class FileTypesBundle {
  private static final @NonNls String BUNDLE = "messages.FileTypesBundle";

  private static final DynamicBundle INSTANCE = new DynamicBundle(FileTypesBundle.class, BUNDLE);

  private FileTypesBundle() {
  }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}
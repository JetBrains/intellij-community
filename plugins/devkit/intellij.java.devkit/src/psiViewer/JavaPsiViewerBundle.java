// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.devkit.psiViewer;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

final class JavaPsiViewerBundle {
  private static final @NonNls String BUNDLE_FQN = "messages.JavaPsiViewerBundle";
  private static final DynamicBundle BUNDLE = new DynamicBundle(JavaPsiViewerBundle.class, BUNDLE_FQN);

  private JavaPsiViewerBundle() {
  }

  static @Nls @NotNull String message(
    @PropertyKey(resourceBundle = BUNDLE_FQN) @NotNull String key,
    @Nullable Object @NotNull ... params) {
    return BUNDLE.getMessage(key, params);
  }
}

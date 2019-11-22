// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BundleAccessor {
  @NotNull
  String message(@NotNull String key, Object... params);

  @Nullable
  String messageOrNull(@NotNull String key, Object... params);
}

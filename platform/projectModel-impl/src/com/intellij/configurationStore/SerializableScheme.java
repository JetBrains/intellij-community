// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore;

import com.intellij.openapi.options.SchemeState;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SerializableScheme {
  @NotNull Element writeScheme();

  /**
   * @implNote return {@code null} if unsure.
   */
  default @Nullable SchemeState getSchemeState() {
    return null;
  }
}

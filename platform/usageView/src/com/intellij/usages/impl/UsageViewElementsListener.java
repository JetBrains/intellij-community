// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;

public interface UsageViewElementsListener {
  ExtensionPointName<UsageViewElementsListener> EP_NAME = ExtensionPointName.create("com.intellij.usageViewElementsListener");

  default void beforeUsageAdded(@NotNull Usage usage) {}

  default boolean isExcludedByDefault(@NotNull UsageView view, @NotNull Usage usage) {
    return false;
  }
}

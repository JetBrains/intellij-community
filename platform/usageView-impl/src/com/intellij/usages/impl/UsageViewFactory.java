// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Factory;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageSearcher;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to create custom usage view instances for representing specific types of usages.
 */
public interface UsageViewFactory {
  ExtensionPointName<UsageViewFactory> EP_NAME = ExtensionPointName.create("com.intellij.usageViewFactory");

  /**
   * Creates a usage view instance for representing the given set of usages and appends the given usages to it.
   * @return the usage view instance or null if this factory does not handle this type of usages.
   */
  @Nullable
  UsageViewEx createUsageView(UsageTarget @NotNull [] targets,
                              Usage @NotNull [] usages,
                              @NotNull UsageViewPresentation presentation,
                              Factory<? extends UsageSearcher> usageSearcherFactory);
}

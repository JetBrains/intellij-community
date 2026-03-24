// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages;

import com.intellij.openapi.util.Factory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class UsageViewManagerWithUsageViewFactoryCallback extends UsageViewManager {
  public abstract @NotNull UsageView showUsages(UsageTarget @NotNull [] searchedFor,
                                                Usage @NotNull [] foundUsages,
                                                @NotNull UsageViewPresentation presentation,
                                                @Nullable Factory<? extends UsageSearcher> usageSearcherFactory,
                                                @NotNull Runnable onUsagesFoundRunnable);

  public abstract @NotNull UsageView createUsageView(UsageTarget @NotNull [] targets,
                                                     Usage @NotNull [] usages,
                                                     @NotNull UsageViewPresentation presentation,
                                                     @Nullable Factory<? extends UsageSearcher> usageSearcherFactory,
                                                     @NotNull Runnable onUsagesFoundRunnable);
}

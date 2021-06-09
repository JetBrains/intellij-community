// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

class DefaultUsageGroup extends UsageGroupBase {

  private final @NotNull Supplier<@NlsContexts.ListItem @NotNull String> myTextSupplier;

  protected DefaultUsageGroup(int order, @NotNull Supplier<@NlsContexts.ListItem @NotNull String> supplier) {
    super(order);
    myTextSupplier = supplier;
  }

  @Override
  public final @NotNull String getText(@Nullable UsageView view) {
    return myTextSupplier.get();
  }
}

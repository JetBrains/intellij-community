// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class AllowedItemsResourceStorage extends AllowedItemsResourceStorageBase {
  @NotNull
  private final Set<String> items;

  public AllowedItemsResourceStorage(@NotNull Class<?> holder, @NotNull String path) {
    super(holder, path);
    items = Collections.unmodifiableSet(readItems());
  }

  @Override
  @NotNull
  public Set<String> getItems() {
    return items;
  }
}
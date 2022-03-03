// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;

public class AllowedItemsResourceWeakRefStorage extends AllowedItemsResourceStorageBase {
  @NotNull
  private WeakReference<Set<String>> itemsRef = new WeakReference<>(null);

  public AllowedItemsResourceWeakRefStorage(@NotNull Class<?> holder, @NotNull String path) {
    super(holder, path);
  }

  @Override
  @NotNull
  public synchronized Set<String> getItems() {
    Set<String> items = itemsRef.get();
    if (items == null) {
      items = Collections.unmodifiableSet(readItems());
      itemsRef = new WeakReference<>(items);
    }

    return items;
  }
}
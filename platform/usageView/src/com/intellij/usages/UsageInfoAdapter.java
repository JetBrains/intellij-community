// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.usageView.UsageInfo;
import com.intellij.usages.rules.MergeableUsage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface UsageInfoAdapter extends Usage, MergeableUsage {
  @NotNull
  String getPath();
  int getLine();
  @Override
  int getNavigationOffset();
  @NotNull UsageInfo @NotNull [] getMergedInfos();
  @NotNull
  CompletableFuture<UsageInfo[]> getMergedInfosAsync();

  @ApiStatus.Internal
  default boolean isLoaded() {
    return true;
  }
}
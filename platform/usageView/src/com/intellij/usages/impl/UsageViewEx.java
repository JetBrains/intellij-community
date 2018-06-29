// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface UsageViewEx extends UsageView {
  boolean searchHasBeenCancelled();

  void cancelCurrentSearch();

  void associateProgress(@NotNull ProgressIndicator indicator);

  void waitForUpdateRequestsCompletion();

  @NotNull
  CompletableFuture<?> appendUsagesInBulk(@NotNull Collection<Usage> usages);

  void setSearchInProgress(boolean searchInProgress);

  void searchFinished();
}

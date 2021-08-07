// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnusedDeclaration")
public final class MainImpl implements StartupUtil.AppStarter {
  public MainImpl() {
    PlatformUtils.setDefaultPrefixForCE();
  }

  @Override
  public @NotNull CompletableFuture<?> start(@NotNull List<String> args, @NotNull CompletableFuture<?> prepareUiFuture) {
    ApplicationLoader.initApplication(args, prepareUiFuture);
    return CompletableFuture.completedFuture(null);
  }
}
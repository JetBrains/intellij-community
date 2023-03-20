// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsLogProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class DataPackBase {
  protected final @NotNull Map<VirtualFile, VcsLogProvider> myLogProviders;
  protected final @NotNull RefsModel myRefsModel;
  protected final boolean myIsFull;

  public DataPackBase(@NotNull Map<VirtualFile, VcsLogProvider> providers, @NotNull RefsModel refsModel, boolean isFull) {
    myLogProviders = providers;
    myRefsModel = refsModel;
    myIsFull = isFull;
  }

  public @NotNull Map<VirtualFile, VcsLogProvider> getLogProviders() {
    return myLogProviders;
  }

  public @NotNull RefsModel getRefsModel() {
    return myRefsModel;
  }

  public boolean isFull() {
    return myIsFull;
  }
}

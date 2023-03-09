// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Map;

public class VcsGoToRefComparator implements Comparator<VcsRef> {
  private final @NotNull Map<VirtualFile, VcsLogProvider> myProviders;

  public VcsGoToRefComparator(@NotNull Map<VirtualFile, VcsLogProvider> providers) {
    myProviders = providers;
  }

  @Override
  public int compare(@NotNull VcsRef ref1, @NotNull VcsRef ref2) {
    VcsLogProvider provider1 = myProviders.get(ref1.getRoot());
    VcsLogProvider provider2 = myProviders.get(ref2.getRoot());

    if (provider1 == null) return provider2 == null ? ref1.getName().compareTo(ref2.getName()) : 1;
    if (provider2 == null) return -1;

    if (provider1 == provider2) {
      return provider1.getReferenceManager().getLabelsOrderComparator().compare(ref1, ref2);
    }

    return provider1.getSupportedVcs().getName().compareTo(provider2.getSupportedVcs().getName());
  }
}

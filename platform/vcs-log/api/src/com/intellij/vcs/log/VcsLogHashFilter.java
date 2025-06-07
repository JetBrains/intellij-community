// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.vcs.log.VcsLogFilterCollection.HASH_FILTER;

public interface VcsLogHashFilter extends VcsLogFilter {

  @NotNull
  Collection<@NlsSafe String> getHashes();

  @Override
  default @NotNull VcsLogFilterCollection.FilterKey<VcsLogHashFilter> getKey() {
    return HASH_FILTER;
  }
}

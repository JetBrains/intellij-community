// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.visible.filters.VcsLogStructureFilterImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

@ApiStatus.Internal
public class VcsLogFileHistoryFilter extends VcsLogStructureFilterImpl {
  private final @Nullable Hash myHash;

  public VcsLogFileHistoryFilter(@NotNull FilePath file, @Nullable Hash hash) {
    super(Collections.singleton(file));
    myHash = hash;
  }

  public @Nullable Hash getHash() {
    return myHash;
  }
}

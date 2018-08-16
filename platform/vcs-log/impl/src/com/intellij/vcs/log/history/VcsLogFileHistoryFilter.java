// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.data.VcsLogStructureFilterImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class VcsLogFileHistoryFilter extends VcsLogStructureFilterImpl {
  @Nullable private final Hash myHash;

  public VcsLogFileHistoryFilter(@NotNull FilePath file, @Nullable Hash hash) {
    super(Collections.singleton(file));
    myHash = hash;
  }

  @Nullable
  public Hash getHash() {
    return myHash;
  }
}

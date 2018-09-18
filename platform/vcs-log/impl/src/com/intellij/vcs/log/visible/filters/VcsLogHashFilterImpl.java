// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters;

import com.intellij.vcs.log.VcsLogHashFilter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class VcsLogHashFilterImpl implements VcsLogHashFilter {

  @NotNull private final Collection<String> myHashes;

  public VcsLogHashFilterImpl(@NotNull Collection<String> hashes) {
    myHashes = hashes;
  }

  @NotNull
  @Override
  public Collection<String> getHashes() {
    return myHashes;
  }

  @Override
  public String toString() {
    return "hashes:" + myHashes;
  }
}

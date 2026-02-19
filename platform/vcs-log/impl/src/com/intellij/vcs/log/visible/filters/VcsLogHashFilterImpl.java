// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible.filters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.vcs.log.VcsLogHashFilter;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;

/**
 * @see VcsLogFilterObject#fromHash(String)
 */
class VcsLogHashFilterImpl implements VcsLogHashFilter {
  private final @NotNull Collection<String> myHashes;

  VcsLogHashFilterImpl(@NotNull Collection<String> hashes) {
    myHashes = hashes;
  }

  @Override
  public @NotNull Collection<String> getHashes() {
    return myHashes;
  }

  @Override
  public @NotNull String getDisplayText() {
    return StringUtil.join(getHashes(), it -> VcsLogUtil.getShortHash(it), ", ");
  }

  @Override
  public @NonNls String toString() {
    return "hashes:" + myHashes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VcsLogHashFilterImpl filter = (VcsLogHashFilterImpl)o;
    return myHashes.equals(filter.myHashes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myHashes);
  }
}

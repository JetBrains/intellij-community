// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.vcs.log.VcsLogHashFilter;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;

class VcsLogHashFilterImpl implements VcsLogHashFilter {
  @NotNull private final Collection<String> myHashes;

  VcsLogHashFilterImpl(@NotNull Collection<String> hashes) {
    myHashes = hashes;
  }

  @NotNull
  @Override
  public Collection<String> getHashes() {
    return myHashes;
  }

  @NotNull
  @Override
  public String getDisplayText() {
    return StringUtil.join(getHashes(), it -> VcsLogUtil.getShortHash(it), ", ");
  }

  @Override
  public String toString() {
    return "hashes:" + myHashes; // NON-NLS
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

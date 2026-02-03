// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea;

import com.intellij.vcs.log.Hash;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Used for storing branch information from repository internal hg files, f.e.  branchheads, bookmarks
 *
 * @see org.zmlx.hg4idea.repo.HgRepositoryReader
 */
public class HgNameWithHashInfo {

  protected final @NotNull String myName;
  private final @NotNull Hash myHash;

  public HgNameWithHashInfo(@NotNull String name, @NotNull Hash hash) {
    myName = name;
    myHash = hash;
  }

  /**
   * <p>Returns the hash on which this bookmark or tag is reference to.</p>
   */
  public @NotNull Hash getHash() {
    return myHash;
  }

  public @NotNull String getName() {
    return myName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HgNameWithHashInfo info = (HgNameWithHashInfo)o;
    return (myName.equals(info.myName)) && myHash.equals(info.myHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, myHash);
  }
}
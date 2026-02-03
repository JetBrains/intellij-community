// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.push;

import com.intellij.dvcs.push.PushTarget;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.util.HgUtil;

public class HgTarget implements PushTarget {
  @NotNull @NlsSafe String myTarget;
  @NotNull @NlsSafe String myBranchName;

  public HgTarget(@NotNull @NlsSafe String name, @NotNull @NlsSafe String branchName) {
    myTarget = name;
    myBranchName = branchName;
  }

  @Override
  public @NotNull String getPresentation() {
    return HgUtil.removePasswordIfNeeded(myTarget);
  }

  @Override
  public boolean hasSomethingToPush() {
    // push is always allowed except invalid target
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof HgTarget hgTarget)) return false;

    if (!myBranchName.equals(hgTarget.myBranchName)) return false;
    if (!myTarget.equals(hgTarget.myTarget)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myTarget.hashCode();
    result = 31 * result + myBranchName.hashCode();
    return result;
  }

  public @NlsSafe @NotNull String getBranchName() {
    return myBranchName;
  }

  @Override
  public String toString() {
    return getPresentation();
  }
}
